package fiji.plugin.cwnt.segmentation;

import ij.IJ;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.MultiThreadedBenchmarkAlgorithm;
import net.imglib2.algorithm.labeling.AllConnectedComponents;
import net.imglib2.img.Img;
import net.imglib2.labeling.Labeling;
import net.imglib2.labeling.NativeImgLabeling;
import net.imglib2.type.NativeType;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Util;
import fiji.plugin.trackmate.Spot;
import fiji.plugin.trackmate.detection.SpotDetector;

public class CrownWearingSegmenter< T extends RealType< T > & NativeType< T >> extends MultiThreadedBenchmarkAlgorithm implements SpotDetector< T >
{

	private Img< FloatType > masked;

	private final RandomAccessibleInterval< T > source;

	private Img< BitType > thresholded;

	private NativeImgLabeling< Integer, IntType > labeling;

	private List< Spot > spots;

	private final double[] calibration;

	private final Map< String, Object > settings;

	/*
	 * CONSTRUCTOR
	 */

	public CrownWearingSegmenter( final RandomAccessibleInterval< T > imFrame, final double[] calibration, final Map< String, Object > settings )
	{
		this.source = imFrame;
		this.calibration = calibration;
		this.settings = settings;
	}

	/*
	 * METHODS
	 */

	@Override
	public List< Spot > getResult()
	{
		return spots;
	}

	@Override
	public String toString()
	{
		return "Crown-Wearing Segmenter";
	}

	@Override
	public boolean checkInput()
	{
		return true;
	}

	@Override
	public boolean process()
	{
		final long start = System.currentTimeMillis();
		boolean check;

		// Crown wearing mask
		final NucleiMasker< T > masker = new NucleiMasker< T >( source );
		masker.setNumThreads( numThreads );
		masker.setParameters( CrownWearingSegmenterFactory.collectMaskingParameters( settings ) );
		check = masker.process();
		if ( check )
		{
			masked = masker.getResult();
		}
		else
		{
			errorMessage = masker.getErrorMessage();
			return false;
		}

		// Thresholding
		final double thresholdFactor = ( Double ) settings.get( CrownWearingSegmenterFactory.THRESHOLD_FACTOR_PARAMETER );
		final OtsuThresholder2D< FloatType > thresholder = new OtsuThresholder2D< FloatType >( masked, thresholdFactor );
		thresholder.setNumThreads( numThreads );
		check = thresholder.process();
		if ( check )
		{
			thresholded = thresholder.getResult();
		}
		else
		{
			errorMessage = thresholder.getErrorMessage();
			return false;
		}

		// Labeling
		final Iterator< Integer > labelGenerator = AllConnectedComponents.getIntegerNames( 0 );
		final Img< IntType > img = Util.getArrayOrCellImgFactory( source, new IntType() ).create( source, new IntType() );
		labeling = new NativeImgLabeling< Integer, IntType >( img );

		// 6-connected structuring element
		final long[][] structuringElement = new long[][] { { -1, 0, 0 }, { 1, 0, 0 }, { 0, -1, 0 }, { 0, 1, 0 }, { 0, 0, -1 }, { 0, 0, 1 } };

		AllConnectedComponents.labelAllConnectedComponents( labeling, thresholded, labelGenerator, structuringElement );

		// Splitting and spot creation
		final NucleiSplitter splitter = new NucleiSplitter( labeling, calibration );
		if ( !( splitter.checkInput() && splitter.process() ) )
		{
			IJ.error( "Problem with splitter: " + splitter.getErrorMessage() );
			return false;
		}
		spots = splitter.getResult();
		processingTime = System.currentTimeMillis() - start;
		return true;
	}


	public Labeling< Integer > getLabeling()
	{
		return labeling;
	}
}
