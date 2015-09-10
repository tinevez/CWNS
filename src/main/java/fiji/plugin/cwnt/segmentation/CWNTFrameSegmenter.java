package fiji.plugin.cwnt.segmentation;

import fiji.plugin.trackmate.util.TMUtils;
import ij.ImagePlus;

import java.util.Map;

import net.imglib2.algorithm.MultiThreadedBenchmarkAlgorithm;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.roi.labeling.ImgLabeling;
import net.imglib2.type.numeric.ARGBType;

public class CWNTFrameSegmenter extends MultiThreadedBenchmarkAlgorithm
{

	private final CWNTPanel source;

	private final ImagePlus imp;

	/*
	 * CONSTRUCTOR
	 */

	public CWNTFrameSegmenter( final CWNTPanel panel )
	{
		super();
		this.source = panel;
		this.imp = panel.getTargetImagePlus();
	}

	/*
	 * METHODS
	 */

	@Override
	public boolean checkInput()
	{
		return true;
	}

	@SuppressWarnings( { "rawtypes", "unchecked" } )
	@Override
	public boolean process()
	{

		final long start = System.currentTimeMillis();
		final int frame = imp.getFrame();

		final Map< String, Object > settings = source.getSettings();

		final CrownWearingSegmenterFactory factory = new CrownWearingSegmenterFactory();
		factory.setTarget( TMUtils.rawWraps( imp ), settings );

		final CrownWearingSegmenter cws = factory.getDetector( null, frame );
		cws.setNumThreads( getNumThreads() );

		if ( !cws.process() )
		{
			errorMessage = cws.getErrorMessage();
			return false;
		}

		final long end = System.currentTimeMillis();
		processingTime = end - start;

		final ImgLabeling labels = cws.getLabeling();
		final LabelToRGB converter = new LabelToRGB( labels );
		converter.setNumThreads( getNumThreads() );
		converter.process();
		final Img< ARGBType > rgb = converter.getResult();

		final ImagePlus result = ImageJFunctions.wrap( rgb, "Labels" );
		result.setCalibration( imp.getCalibration() );
		result.show();

		final int tmin = ( int ) Math.ceil( processingTime / 1e3 / 60 ); // min
		source.labelDurationEstimate.setText( "Total duration rough estimate: " + tmin + " min." );

		return true;
	}

}
