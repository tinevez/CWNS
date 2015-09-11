package fiji.plugin.cwnt.segmentation;

import static fiji.plugin.trackmate.detection.DetectorKeys.KEY_TARGET_CHANNEL;
import static fiji.plugin.trackmate.io.IOUtils.readBooleanAttribute;
import static fiji.plugin.trackmate.io.IOUtils.readDoubleAttribute;
import static fiji.plugin.trackmate.io.IOUtils.readIntegerAttribute;
import static fiji.plugin.trackmate.io.IOUtils.writeAttribute;
import static fiji.plugin.trackmate.util.TMUtils.checkMapKeys;
import static fiji.plugin.trackmate.util.TMUtils.checkParameter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.ImageIcon;

import net.imagej.ImgPlus;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.Views;

import org.jdom2.Element;

import fiji.plugin.cwnt.CWNT_;
import fiji.plugin.trackmate.Logger.StringBuilderLogger;
import fiji.plugin.trackmate.Model;
import fiji.plugin.trackmate.Settings;
import fiji.plugin.trackmate.detection.SpotDetectorFactory;
import fiji.plugin.trackmate.gui.ConfigurationPanel;
import fiji.plugin.trackmate.util.TMUtils;

@SuppressWarnings( "deprecation" )
//@Plugin( type = SpotDetectorFactory.class )
public class CrownWearingSegmenterFactory< T extends RealType< T > & NativeType< T >> implements SpotDetectorFactory< T >
{

	/*
	 * CONSTANTS
	 */

	/** A string key identifying this factory. */
	public static final String DETECTOR_KEY = "CWNS_DETECTOR";

	/** The pretty name of the target detector. */
	public static final String NAME = "CWNS";

	/** An html information text. */
	public static final String INFO_TEXT = "<html>" +
			"<div align=\"justify\">" +
			"Crown-wearing nuclei segmenter. " +
			"<p> " +
			"This plugin allows the segmentation and tracking of bright blobs objects, " +
			"typically nuclei imaged in 3D over time. " +
			"<p> " +
			"It is specially designed to deal with the case the developing zebra-fish " +
			"embryogenesis, where nuclei are densily packed, which complicates their detection. " +
			"To do so, this plugin operates in 2 steps:" +
			"<p>" +
			" - The image is first pre-processed, by computing a special mask that stresses" +
			"the nuclei boundaries. A crown-like mak is computed from the 2D spatial derivatives " +
			"of the image, and a masked image where the nuclei are better separated is generated. " +
			"<br>" +
			" - Then the nuclei are thresholded from the background of the masked image, " +
			"labeled in 3D and tracked over time. " +
			"<p>" +
			"Because the crown-like mask needs 9 parameters to be specified, this plugin offers " +
			"to test the value of paramters in the 2nd and 3rd tab of this GUI. The resulting masked" +
			"image and intermediate images will be computed over a limited area of the source image, " +
			"specified by the ROI. " +
			"<p> " +
			"Once you are happy with the parameters, mode to the 4th tab to launch the computation " +
			"in batch." +
			"</div>" +
			"<div align=\"right\"> " +
			"<tt>" +
			"Bhavna Rajasekaran <br>" +
			"Jean-Yves Tinevez <br>" +
			"Andrew Oates lab - MPI-CBG, Dresden, 2011 " +
			"</tt>" +
			"</div> "
			+ "<p> "
			+ "<code>" + CWNT_.PLUGIN_NAME + " v" + CWNT_.PLUGIN_VERSION + "</code>" +
			"</html>" +
			"";

	/*
	 * FIELDS
	 */

	/** The image to operate on. Multiple frames, single channel. */
	protected ImgPlus< T > img;

	protected Map< String, Object > settings;

	protected String errorMessage;

	/*
	 * METHODS
	 */

	@Override
	public String getInfoText()
	{
		return INFO_TEXT;
	}

	@Override
	public ImageIcon getIcon()
	{
		return null;
	}

	@Override
	public String getKey()
	{
		return DETECTOR_KEY;
	}

	@Override
	public String getName()
	{
		return NAME;
	}

	@Override
	public CrownWearingSegmenter< T > getDetector( final Interval interval, final int frame )
	{
		final double[] calibration = TMUtils.getSpatialCalibration( img );
		RandomAccessibleInterval< T > imFrame;
		final int cDim = TMUtils.findCAxisIndex( img );
		if ( cDim < 0 )
		{
			imFrame = img;
		}
		else
		{
			// In ImgLib2, dimensions are 0-based.
			final int channel = ( Integer ) settings.get( KEY_TARGET_CHANNEL ) - 1;
			imFrame = Views.hyperSlice( img, cDim, channel );
		}

		int timeDim = TMUtils.findTAxisIndex( img );
		if ( timeDim >= 0 )
		{
			if ( cDim >= 0 && timeDim > cDim )
			{
				timeDim--;
			}
			imFrame = Views.hyperSlice( imFrame, timeDim, frame );
		}

		// In case we have a 1D image.
		if ( img.dimension( 0 ) < 2 )
		{ // Single column image, will be rotated internally.
			calibration[ 0 ] = calibration[ 1 ]; // It gets NaN otherwise
			calibration[ 1 ] = 1;
			imFrame = Views.hyperSlice( imFrame, 0, 0 );
		}
		if ( img.dimension( 1 ) < 2 )
		{ // Single line image
			imFrame = Views.hyperSlice( imFrame, 1, 0 );
		}

		final CrownWearingSegmenter< T > detector = new CrownWearingSegmenter< T >( imFrame, calibration, settings );
		detector.setNumThreads( 1 );
		return detector;
	}

	@Override
	public boolean setTarget( final ImgPlus< T > img, final Map< String, Object > settings )
	{
		this.img = img;
		this.settings = settings;
		return checkSettings( settings );
	}

	@Override
	public String getErrorMessage()
	{
		return errorMessage;
	}

	@Override
	public boolean marshall( final Map< String, Object > settings, final Element element )
	{

		final StringBuilder errorHolder = new StringBuilder();
		boolean ok = true;
		for ( final String param : PARAMETERS_DOUBLES )
		{
			ok = ok && writeAttribute( settings, element, param, Double.class, errorHolder );
		}
		for ( final String param : PARAMETERS_INTEGERS )
		{
			ok = ok && writeAttribute( settings, element, param, Integer.class, errorHolder );
		}
		ok = ok && writeAttribute( settings, element, KEY_SPLIT_NUCLEI, Boolean.class, errorHolder );

		if ( !ok )
		{
			errorMessage = errorHolder.toString();
		}
		return ok;
	}

	@Override
	public boolean unmarshall( final Element element, final Map< String, Object > settings )
	{
		settings.clear();

		final StringBuilder errorHolder = new StringBuilder();
		final StringBuilderLogger logger = new StringBuilderLogger( errorHolder );
		boolean ok = true;
		for ( final String param : PARAMETERS_DOUBLES )
		{
			ok = ok & readDoubleAttribute( element, settings, param, errorHolder );
		}
		for ( final String param : PARAMETERS_INTEGERS )
		{
			ok = ok & readIntegerAttribute( element, settings, param, errorHolder );
		}
		ok = ok & readBooleanAttribute( element, KEY_SPLIT_NUCLEI, logger );

		if ( !ok )
		{
			errorMessage = errorHolder.toString();
			return false;
		}
		return checkSettings( settings );
	}

	@Override
	public ConfigurationPanel getDetectorConfigurationPanel( final Settings settings, final Model model )
	{
		return new CWNTPanel( settings.imp );
	}

	@Override
	public Map< String, Object > getDefaultSettings()
	{
		final Map< String, Object > settings = new HashMap< String, Object >();
		settings.put( SIGMA_F_PARAMETER, 0.5 );
		settings.put( N_AD_PARAMETER, 5 );
		settings.put( KAPPA_PARAMETER, 50.0 );
		settings.put( SIGMA_G_PARAMETER, 1.0 );
		settings.put( GAMMA_PARAMETER, 1.0 );
		settings.put( ALPHA_PARAMETER, 1.0 );
		settings.put( BETA_PARAMETER, 1.0 );
		settings.put( EPSILON_PARAMETER, 1.0 );
		settings.put( DELTA_PARAMETER, 1.0 );
		settings.put( THRESHOLD_FACTOR_PARAMETER, 1.6 );
		settings.put( KEY_SPLIT_NUCLEI, Boolean.valueOf( true ) );
		return settings;
	}

	@Override
	public boolean checkSettings( final Map< String, Object > settings )
	{
		boolean ok = true;
		final StringBuilder errorHolder = new StringBuilder();
		for ( final String param : PARAMETERS_DOUBLES )
		{
			ok = ok & checkParameter( settings, param, Double.class, errorHolder );
		}
		for ( final String param : PARAMETERS_INTEGERS )
		{
			ok = ok & checkParameter( settings, param, Integer.class, errorHolder );
		}
		ok = ok & checkParameter( settings, KEY_SPLIT_NUCLEI, Boolean.class, errorHolder );

		ok = ok & checkMapKeys( settings, PARAMETER_NAMES, null, errorHolder );
		if ( !ok )
		{
			errorMessage = errorHolder.toString();
		}
		return ok;
	}

	public static double[] collectMaskingParameters( final Map< String, Object > settings )
	{
		final double[] maskingParams = new double[9];
		maskingParams [ 0 ] = ( Double ) settings.get( SIGMA_F_PARAMETER );
		maskingParams[ 1 ] = ( Double ) settings.get( N_AD_PARAMETER );
		maskingParams[ 2 ] = ( Double ) settings.get( KAPPA_PARAMETER );
		maskingParams[ 3 ] = ( Double ) settings.get( SIGMA_G_PARAMETER );
		maskingParams[ 4 ] = ( Double ) settings.get( GAMMA_PARAMETER );
		maskingParams[ 5 ] = ( Double ) settings.get( ALPHA_PARAMETER );
		maskingParams[ 6 ] = ( Double ) settings.get( BETA_PARAMETER );
		maskingParams[ 7 ] = ( Double ) settings.get( EPSILON_PARAMETER );
		maskingParams[ 8 ] = ( Double ) settings.get( DELTA_PARAMETER );
		return maskingParams;
	}

	public static void putMaskingParameters( final double[] params, final Map< String, Object > settings )
	{
		settings.put( SIGMA_F_PARAMETER, params[ 0 ] );
		settings.put( N_AD_PARAMETER, params[ 1 ] );
		settings.put( KAPPA_PARAMETER, params[ 2 ] );
		settings.put( SIGMA_G_PARAMETER, params[ 3 ] );
		settings.put( GAMMA_PARAMETER, params[ 4 ] );
		settings.put( ALPHA_PARAMETER, params[ 5 ] );
		settings.put( BETA_PARAMETER, params[ 6 ] );
		settings.put( EPSILON_PARAMETER, params[ 7 ] );
		settings.put( DELTA_PARAMETER, params[ 8 ] );
	}

	/*
	 * PARAMETER NAMES
	 */

	public static final String SIGMA_F_PARAMETER = "sigmaf";

	public static final String SIGMA_G_PARAMETER = "sigmag";

	public static final String N_AD_PARAMETER = "nAD";

	public static final String KAPPA_PARAMETER = "kapa";

	public static final String ALPHA_PARAMETER = "alpha";

	public static final String BETA_PARAMETER = "beta";

	public static final String GAMMA_PARAMETER = "gamma";

	public static final String DELTA_PARAMETER = "delta";

	public static final String EPSILON_PARAMETER = "epsilon";

	public static final String THRESHOLD_FACTOR_PARAMETER = "thresholdFactor";

	public static final String KEY_SPLIT_NUCLEI = "splitNuclei";

	public static final List< String > PARAMETER_NAMES = Arrays.asList( new String[]
	{
			SIGMA_F_PARAMETER,
			SIGMA_G_PARAMETER,
			N_AD_PARAMETER,
			KAPPA_PARAMETER,
			ALPHA_PARAMETER,
			BETA_PARAMETER,
			GAMMA_PARAMETER,
			DELTA_PARAMETER,
			EPSILON_PARAMETER,
			THRESHOLD_FACTOR_PARAMETER,
			KEY_TARGET_CHANNEL,
			KEY_SPLIT_NUCLEI
	}
			);

	private static final List< String > PARAMETERS_DOUBLES;

	private static final List< String > PARAMETERS_INTEGERS;
	static
	{
		PARAMETERS_DOUBLES = new ArrayList< String >( PARAMETER_NAMES );
		PARAMETERS_DOUBLES.remove( N_AD_PARAMETER );
		PARAMETERS_DOUBLES.remove( KEY_TARGET_CHANNEL );
		PARAMETERS_INTEGERS = new ArrayList< String >( 2 );
		PARAMETERS_INTEGERS.add( N_AD_PARAMETER );
		PARAMETERS_INTEGERS.add( KEY_TARGET_CHANNEL );
	}
}
