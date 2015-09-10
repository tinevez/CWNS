package fiji.plugin.cwnt.segmentation;

import ij.IJ;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.MultiThreadedBenchmarkAlgorithm;
import net.imglib2.algorithm.labeling.ConnectedComponents;
import net.imglib2.algorithm.labeling.ConnectedComponents.StructuringElement;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.roi.labeling.ImgLabeling;
import net.imglib2.type.NativeType;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedIntType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Util;
import fiji.plugin.trackmate.Spot;
import fiji.plugin.trackmate.detection.SpotDetector;

public class CrownWearingSegmenter< T extends RealType< T > & NativeType< T >> extends MultiThreadedBenchmarkAlgorithm implements SpotDetector< T >
{

	private static final boolean DEBUG = false;

	private Img< FloatType > masked;

	private final RandomAccessibleInterval< T > source;

	private Img< BitType > thresholded;

	private List< Spot > spots;

	private final double[] calibration;

	private final Map< String, Object > settings;

	private ImgLabeling< Integer, UnsignedIntType > labeling;

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
		if ( DEBUG )
			System.out.println( "Thresholding..." );
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
		if ( DEBUG )
		{
			System.out.println( "Thresholding done." );
			ImageJFunctions.show( thresholded, "Thresholded" );
		}


		// Labeling
		if ( DEBUG )
			System.out.println( "Labelling..." );

		final StructuringElement se = ConnectedComponents.StructuringElement.FOUR_CONNECTED;
		final Img< UnsignedIntType > img = Util.getArrayOrCellImgFactory( thresholded, new UnsignedIntType() ).create( thresholded, new UnsignedIntType() );
		labeling = new ImgLabeling< Integer, UnsignedIntType >( img );
		final Iterator< Integer > labelGenerator = new Iterator< Integer >()
		{
			private int val = 0;

			@Override
			public Integer next()
			{
				val++;
				return Integer.valueOf( val );
			}

			@Override
			public boolean hasNext()
			{
				return true;
			}
		};
		final ExecutorService service = Executors.newFixedThreadPool( numThreads );
		ConnectedComponents.labelAllConnectedComponents( thresholded, labeling, labelGenerator, se, service );
		service.shutdown();

		if ( DEBUG )
		{
			System.out.println( "Labelling done." );
			ImageJFunctions.show( img, "Labels." );
		}

		// Splitting and spot creation
		if ( DEBUG )
		{
			System.out.println( "Nuclei splitting..." );
			System.out.println( "Spatial calibration: " + Util.printCoordinates( calibration ) );

		}
		final NucleiSplitter splitter = new NucleiSplitter( labeling, calibration, labelGenerator );
		if ( !( splitter.checkInput() && splitter.process() ) )
		{
			IJ.error( "Problem with splitter: " + splitter.getErrorMessage() );
			return false;
		}
		if ( DEBUG )
			System.out.println( "Splitting done." );

		spots = splitter.getResult();
		processingTime = System.currentTimeMillis() - start;
		return true;
	}


	public ImgLabeling< Integer, UnsignedIntType > getLabeling()
	{
		return labeling;
	}
}
