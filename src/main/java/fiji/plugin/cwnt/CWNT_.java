package fiji.plugin.cwnt;

import fiji.plugin.cwnt.gui.CwntGui;
import fiji.plugin.cwnt.segmentation.CrownWearingSegmenter;
import fiji.plugin.cwnt.segmentation.CrownWearingSegmenterFactory;
import fiji.plugin.cwnt.segmentation.LabelToRGB;
import fiji.plugin.cwnt.segmentation.NucleiMasker;
import fiji.plugin.trackmate.Logger;
import fiji.plugin.trackmate.Model;
import fiji.plugin.trackmate.SelectionModel;
import fiji.plugin.trackmate.Settings;
import fiji.plugin.trackmate.Spot;
import fiji.plugin.trackmate.SpotCollection;
import fiji.plugin.trackmate.io.TmXmlWriter;
import fiji.plugin.trackmate.tracking.SpotTracker;
import fiji.plugin.trackmate.tracking.TrackerKeys;
import fiji.plugin.trackmate.tracking.sparselap.SparseLAPTrackerFactory;
import fiji.plugin.trackmate.util.TMUtils;
import fiji.plugin.trackmate.visualization.TrackMateModelView;
import fiji.plugin.trackmate.visualization.hyperstack.HyperStackDisplayer;
import fiji.plugin.trackmate.visualization.hyperstack.SpotOverlay;
import fiji.plugin.trackmate.visualization.hyperstack.TrackOverlay;
import ij.CompositeImage;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.gui.NewImage;
import ij.plugin.Duplicator;
import ij.plugin.PlugIn;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.JFrame;

import net.imglib2.img.ImagePlusAdapter;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.basictypeaccess.array.FloatArray;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.labeling.Labeling;
import net.imglib2.multithreading.SimpleMultiThreading;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.real.FloatType;

public class CWNT_ implements PlugIn
{

	/*
	 * FIELDS
	 */

	private NucleiMasker< ? extends IntegerType< ? >> algo;

	private CwntGui gui;

	public static final String PLUGIN_NAME = "Crown-Wearing Nuclei Tracker ß2";

	private int stepUpdateToPerform = Integer.MAX_VALUE;

	private final DisplayUpdater updater = new DisplayUpdater();

	private Model model;

	private Logger logger;

	private HyperStackDisplayer view;

	private CompositeImage comp2;

	private CompositeImage comp1;

	@Override
	public void run( final String arg )
	{

		// Get current image sample
		final ImagePlus imp = WindowManager.getCurrentImage();
		if ( null == imp )
			return;

		// Create Panel silently
		gui = new CwntGui( imp );
		logger = Logger.IJ_LOGGER;

		// Add listeners
		imp.getCanvas().addMouseListener( new MouseAdapter()
		{

			@Override
			public void mouseReleased( final MouseEvent e )
			{
				if ( gui.getSelectedIndex() == gui.indexPanelParameters2 || gui.getSelectedIndex() == gui.indexPanelParameters1 )
				{
					new Thread( "CWNT tuning thread" )
					{
						@Override
						public void run()
						{
							recomputeSampleWindows( imp );
						};
					}.start();
				}
			}
		} );

		final JFrame frame = new JFrame( PLUGIN_NAME );
		frame.setSize( 400, 600 );
		frame.addWindowListener( new WindowListener()
		{
			@Override
			public void windowOpened( final WindowEvent e )
			{}

			@Override
			public void windowIconified( final WindowEvent e )
			{}

			@Override
			public void windowDeiconified( final WindowEvent e )
			{}

			@Override
			public void windowDeactivated( final WindowEvent e )
			{}

			@Override
			public void windowClosing( final WindowEvent e )
			{}

			@Override
			public void windowClosed( final WindowEvent e )
			{
				updater.quit();
			}

			@Override
			public void windowActivated( final WindowEvent e )
			{}
		} );

		gui.addActionListener( new ActionListener()
		{

			@Override
			public void actionPerformed( final ActionEvent e )
			{

				if ( e == gui.STEP1_PARAMETER_CHANGED )
				{
					stepUpdateToPerform = Math.min( 1, stepUpdateToPerform );
					updater.doUpdate();

				}
				else if ( e == gui.STEP2_PARAMETER_CHANGED )
				{
					stepUpdateToPerform = Math.min( 2, stepUpdateToPerform );
					updater.doUpdate();

				}
				else if ( e == gui.STEP3_PARAMETER_CHANGED )
				{
					stepUpdateToPerform = Math.min( 3, stepUpdateToPerform );
					updater.doUpdate();

				}
				else if ( e == gui.STEP4_PARAMETER_CHANGED )
				{
					stepUpdateToPerform = Math.min( 4, stepUpdateToPerform );
					updater.doUpdate();

				}
				else if ( e == gui.TAB_CHANGED )
				{

					if ( comp1 == null && comp2 == null && ( gui.getSelectedIndex() == gui.indexPanelParameters2 || gui.getSelectedIndex() == gui.indexPanelParameters1 ) )
					{
						new Thread( "CWNT tuning thread" )
						{
							@Override
							public void run()
							{
								recomputeSampleWindows( imp );
							};
						}.start();
					}

				}
				else if ( e == gui.GO_BUTTON_PRESSED )
				{
					new Thread( "CWNT computation thread" )
					{
						@Override
						public void run()
						{
							gui.btnGo.setEnabled( false );
							try
							{
								process( imp );
							}
							finally
							{
								gui.btnGo.setEnabled( true );
							}
						};
					}.start();
				}
				else
				{
					System.err.println( "Unknwon event caught: " + e );
				}
			}
		} );

		frame.getContentPane().add( gui );
		frame.setVisible( true );
	}

	/*
	 * BATCH PROCESS METHODS
	 */

	public void process( final ImagePlus imp )
	{

		final SimpleDateFormat ft = new SimpleDateFormat( "yyyy-MM-dd 'at' HH:mm:ss" );
		Date dNow = new Date();
		logger.log( "Crown-Wearing Nuclei Tracker\n" );
		logger.log( "----------------------------\n" );
		logger.log( ft.format( dNow ) + "\n" );

		final Settings settings = new Settings();
		settings.setFrom( imp );

		final Model model = execSegmentation( settings );
		launchDisplayer( model, imp );
		execTracking( model, settings );
		saveResults( model, settings );
		gui.setModelAndView( model, view );

		logger.setStatus( "" );
		logger.setProgress( 0f );
		dNow = new Date();
		logger.log( "CWNT process finished.\n" );
		logger.log( ft.format( dNow ) + "\n" );
		logger.log( "----------------------------\n" );

	}

	private void saveResults( final Model model, final Settings settings )
	{

		final ImagePlus imp = settings.imp;
		final String dir = imp.getOriginalFileInfo().directory;
		String name = imp.getOriginalFileInfo().fileName;
		name = name.substring( 0, name.lastIndexOf( '.' ) ) + ".xml";
		final File file = new File( dir, name );

		logger.log( "Saving to file " + file.getAbsolutePath() + "...\n" );
		final TmXmlWriter writer = new TmXmlWriter( file );
		writer.appendSettings( settings );
		writer.appendModel( model );
		try
		{
			writer.writeToFile();
		}
		catch ( final FileNotFoundException e )
		{
			e.printStackTrace();
		}
		catch ( final IOException e )
		{
			e.printStackTrace();
		}
		logger.log( "Saving done.\n" );
	}

	private void execTracking( final Model model, final Settings settings )
	{
		final long start = System.currentTimeMillis();

		final SparseLAPTrackerFactory trackerFactory = new SparseLAPTrackerFactory();

		// Prepare tracking settings
		final Map< String, Object > trackerSettings = trackerFactory.getDefaultSettings();
		trackerSettings.put( TrackerKeys.KEY_ALLOW_GAP_CLOSING, true );
		trackerSettings.put( TrackerKeys.KEY_ALLOW_TRACK_MERGING, false );
		trackerSettings.put( TrackerKeys.KEY_ALLOW_TRACK_SPLITTING, false );

		// Evaluate max dist
		logger.log( "Evaluating max linking distance...\n" );
		double maxDist = 0;
		for ( final Spot spot : model.getSpots().iterable( true ) )
		{
			maxDist += spot.getFeature( Spot.RADIUS );
		}
		maxDist /= model.getSpots().getNSpots( true );
		maxDist *= 2;
		logger.log( String.format( "Max linking distance evaluated to %.1f %s.\n", maxDist, model.getSpaceUnits() ) );

		trackerSettings.put( TrackerKeys.KEY_LINKING_MAX_DISTANCE, maxDist );
		trackerSettings.put( TrackerKeys.KEY_GAP_CLOSING_MAX_DISTANCE, 2 * maxDist );
		trackerSettings.put( TrackerKeys.KEY_GAP_CLOSING_MAX_FRAME_GAP, 2 );

		settings.trackerSettings = trackerSettings;

		logger.log( "Performing track linking...\n" );
		logger.setStatus( "Tracking..." );

		final SpotTracker tracker = trackerFactory.create( model.getSpots(), trackerSettings );

		tracker.setLogger( logger );
		tracker.setNumThreads( 1 ); // For memory preservation
		if ( !( tracker.checkInput() && tracker.process() ) )
		{
			logger.error( tracker.getErrorMessage() );
			return;
		}

		model.setTracks( tracker.getResult(), true );

		final long end = System.currentTimeMillis();
		logger.log( String.format( "Track linking completed in %.1f s.\n", ( ( end - start ) / 1e3 ) ) );
		logger.log( String.format( "Found %d tracks.\n", model.getTrackModel().nTracks( true ) ) );
	}

	@SuppressWarnings( { "rawtypes", "unchecked", "deprecation" } )
	private Model execSegmentation( final Settings settings )
	{
		if ( settings.dt == 0 )
		{
			settings.dt = 1;
		}

		final double[] calibration = TMUtils.getSpatialCalibration( settings.imp );
		final SpotCollection allSpots = new SpotCollection();

		final CrownWearingSegmenterFactory factory = new CrownWearingSegmenterFactory();
		factory.setTarget( TMUtils.rawWraps( settings.imp ), gui.getSettings() );

		logger.log( settings.toString() );
		logger.setStatus( "Segmenting..." );

		/*
		 * Fine tune multi-threading: If we have 10 threads and 15 frames to
		 * process, we process 10 frames at once, and allocate 1 thread per
		 * frame. But if we have 10 threads and 2 frames, we process the 2
		 * frames at once, and allocate 5 threads per frame if we can.
		 */
		final int numThreads = Runtime.getRuntime().availableProcessors();
		final int numFrames = settings.imp.getNFrames();
		final int nSimultaneousFrames = Math.min( numThreads, numFrames );
		final int threadsPerFrame = Math.max( 1, numThreads / nSimultaneousFrames );

		final Thread[] threads = SimpleMultiThreading.newThreads( nSimultaneousFrames );
		final AtomicBoolean ok = new AtomicBoolean( true );

		/*
		 * Label ImagePlus holder.
		 */

		final boolean genLabelImg = gui.getGenLabelFlag();
		final ImagePlus labelImp;
		if ( genLabelImg )
		{
			final ImagePlus imp = settings.imp;
			final int width = imp.getWidth();
			final int height = imp.getHeight();
			final int slices = imp.getStackSize();
			final int options = NewImage.FILL_BLACK;
			labelImp = NewImage.createRGBImage( "Labels for " + imp.getTitle(), width, height, slices, options );
			labelImp.setCalibration( imp.getCalibration() );
			labelImp.setDimensions( imp.getNChannels(), imp.getNSlices(), imp.getNFrames() );
		}
		else
		{
			labelImp = null;
		}

		// Prepare the thread array
		final AtomicInteger ai = new AtomicInteger( settings.tstart );
		for ( int ithread = 0; ithread < threads.length; ithread++ )
		{

			threads[ ithread ] = new Thread( "CWNT segmentation thread " + ( 1 + ithread ) + "/" + threads.length )
			{

				private boolean wasInterrupted()
				{
					try
					{
						if ( isInterrupted() )
							return true;
						sleep( 0 );
						return false;
					}
					catch ( final InterruptedException e )
					{
						return true;
					}
				}

				@Override
				public void run()
				{

					for ( int frame = ai.getAndIncrement(); frame <= settings.tend; frame = ai.getAndIncrement() )
					{
						try
						{
							if ( !ok.get() )
							{
								break;
							}

							// Instantiate segmenter
							final CrownWearingSegmenter segmenter = factory.getDetector( null, frame );
							segmenter.setNumThreads( threadsPerFrame );

							// Exec
							if ( !( segmenter.checkInput() && segmenter.process() ) )
							{
								ok.set( false );
								logger.error( "Problem with segmenter: " + segmenter.getErrorMessage() );
								return;
							}
							final List< Spot > spots = segmenter.getResult();

							TMUtils.translateSpots( spots,
									settings.xstart * calibration[ 0 ],
									settings.ystart * calibration[ 1 ],
									settings.zstart * calibration[ 2 ] );

							// Tune time features
							final double t = frame * settings.dt;
							for ( final Spot spot : spots )
							{
								spot.putFeature( Spot.POSITION_T, t );
							}

							allSpots.put( frame, spots );
							logger.setProgress( ( double ) ( frame + 1 ) / settings.imp.getNFrames() );
							logger.log( String.format( "Frame %3d: found %d nuclei in %.1f s.\n",
									( frame + 1 ), spots.size(), ( segmenter.getProcessingTime() / 1e3 ) ) );

							/*
							 * Harvest label image.
							 */

							if ( genLabelImg )
							{
								final Labeling< Integer > labels = segmenter.getLabeling();
								final LabelToRGB rgbConverter = new LabelToRGB( labels );
								rgbConverter.setNumThreads( threadsPerFrame );
								
								if ( !rgbConverter.checkInput() || !rgbConverter.process() )
								{
									ok.set( false );
									logger.error( "Problem with labels: " + rgbConverter.getErrorMessage() );
								}
								else
								{
									final Img< ARGBType > rgb = rgbConverter.getResult();
									final ImagePlus rgbImp = ImageJFunctions.wrapRGB( rgb, "RGB frame " + frame );
									final ImageStack stack = rgbImp.getImageStack();
									for ( int i = 0; i < rgbImp.getStackSize(); i++ )
									{
										final ImageProcessor ip = stack.getProcessor( i + 1 ).duplicate();
										final int n = labelImp.getStackIndex( 1, i + 1, frame + 1 );
										labelImp.getStack().setProcessor( ip, n );
									}
								}
							}
						}
						catch ( final RuntimeException e )
						{
							final Throwable cause = e.getCause();
							if ( cause != null && cause instanceof InterruptedException ) { return; }
							throw e;
						}
					}
				}
			};
		}
		
		SimpleMultiThreading.startAndJoin( threads );
		
		allSpots.setVisible( true );
		if ( genLabelImg )
		{
			labelImp.show();
		}

		logger.setProgress( 0 );
		logger.setStatus( "" );

		model = new Model();
		model.setSpots( allSpots, false );
		model.setPhysicalUnits( settings.imp.getCalibration().getUnit(), settings.imp.getCalibration().getTimeUnit() );

		return model;

	}

	private void launchDisplayer( final Model model, final ImagePlus imp )
	{
		view = createLocalSliceDisplayer( model, imp );
		logger.log( "Rendering segmentation results...\n" );
		view.setDisplaySettings( TrackMateModelView.KEY_TRACK_DISPLAY_MODE, TrackMateModelView.TRACK_DISPLAY_MODE_LOCAL_QUICK );
		view.render();
		logger.log( "Rendering done.\n" );

	}

	public static HyperStackDisplayer createLocalSliceDisplayer( final Model model, final ImagePlus imp )
	{

		return new HyperStackDisplayer( model, new SelectionModel( model ), imp )
		{

			@SuppressWarnings( "serial" )
			@Override
			protected SpotOverlay createSpotOverlay()
			{

				return new SpotOverlay( model, imp, displaySettings )
				{
					@SuppressWarnings( "unused" )
					public void drawSpot( final Graphics2D g2d, final Spot spot, final float zslice,
							final int xcorner, final int ycorner, final float magnification )
					{

						final double x = spot.getFeature( Spot.POSITION_X );
						final double y = spot.getFeature( Spot.POSITION_Y );
						final double z = spot.getFeature( Spot.POSITION_Z );
						final double dz2 = ( z - zslice ) * ( z - zslice );
						final double radiusRatio = ( Float ) displaySettings.get( TrackMateModelView.KEY_SPOT_RADIUS_RATIO );
						final double radius = spot.getFeature( Spot.RADIUS ) * radiusRatio;
						if ( dz2 >= radius * radius )
							return;

						// In pixel units
						final double xp = x / calibration[ 0 ];
						final double yp = y / calibration[ 1 ];
						// Scale to image zoom
						final double xs = ( xp - xcorner ) * magnification;
						final double ys = ( yp - ycorner ) * magnification;

						final double apparentRadius = ( float ) ( Math.sqrt( radius * radius - dz2 ) / calibration[ 0 ] * magnification );
						g2d.drawOval(
								( int ) Math.round( xs - apparentRadius ),
								( int ) Math.round( ys - apparentRadius ),
								( int ) Math.round( 2 * apparentRadius ),
								( int ) Math.round( 2 * apparentRadius ) );
						final boolean spotNameVisible = ( Boolean ) displaySettings.get( TrackMateModelView.KEY_DISPLAY_SPOT_NAMES );
						if ( spotNameVisible )
						{
							final String str = spot.toString();
							final int xindent = fm.stringWidth( str ) / 2;
							final int yindent = fm.getAscent() / 2;
							g2d.drawString(
									spot.toString(),
									( int ) xs - xindent,
									( int ) ys + yindent );
						}
					}
				};
			}

			@SuppressWarnings( "serial" )
			@Override
			protected TrackOverlay createTrackOverlay()
			{

				return new TrackOverlay( model, imp, displaySettings )
				{

					@Override
					protected void drawEdge( final Graphics2D g2d, final Spot source, final Spot target, final int xcorner, final int ycorner, final double magnification )
					{
						// Find x & y in physical coordinates
						final double x0i = source.getFeature( Spot.POSITION_X );
						final double y0i = source.getFeature( Spot.POSITION_Y );
						final double z0i = source.getFeature( Spot.POSITION_Z );
						final double x1i = target.getFeature( Spot.POSITION_X );
						final double y1i = target.getFeature( Spot.POSITION_Y );
						final double z1i = target.getFeature( Spot.POSITION_Z );
						// In pixel units
						final double x0p = x0i / calibration[ 0 ];
						final double y0p = y0i / calibration[ 1 ];
						final double z0p = z0i / calibration[ 2 ];
						final double x1p = x1i / calibration[ 0 ];
						final double y1p = y1i / calibration[ 1 ];
						final double z1p = z1i / calibration[ 2 ];
						// Check if we are nearing their plane
						final int czp = ( imp.getSlice() - 1 );
						if ( Math.abs( czp - z1p ) > 3 && Math.abs( czp - z0p ) > 3 ) { return; }
						// Scale to image zoom
						final double x0s = ( x0p - xcorner ) * magnification;
						final double y0s = ( y0p - ycorner ) * magnification;
						final double x1s = ( x1p - xcorner ) * magnification;
						final double y1s = ( y1p - ycorner ) * magnification;
						// Round
						final int x0 = ( int ) Math.round( x0s );
						final int y0 = ( int ) Math.round( y0s );
						final int x1 = ( int ) Math.round( x1s );
						final int y1 = ( int ) Math.round( y1s );

						g2d.drawLine( x0, y0, x1, y1 );
					}
				};

			}

		};

	}

	/*
	 * PREVIEW METHODS
	 */

	@SuppressWarnings( { "rawtypes", "unchecked" } )
	private void recomputeSampleWindows( final ImagePlus imp )
	{
		final ImagePlus snip = new Duplicator().run( imp, imp.getSlice(), imp.getSlice() );

		// Copy to Imglib
		Img< ? extends IntegerType< ? >> img = null;
		switch ( imp.getType() )
		{
		case ImagePlus.GRAY8:
			img = ImagePlusAdapter.wrapByte( snip );
			break;
		case ImagePlus.GRAY16:
			img = ImagePlusAdapter.wrapShort( snip );
			break;
		default:
			System.err.println( "Image type not handled: " + imp.getType() );
			return;
		}

		// Prepare algo
		algo = new NucleiMasker( img );
		algo.setParameters( gui.getParameters() );
		final boolean check = algo.checkInput() && algo.process();
		if ( !check )
		{
			System.err.println( "Problem with the segmenter: " + algo.getErrorMessage() );
			return;
		}

		final double snipPixels = snip.getWidth() * snip.getHeight() * snip.getNSlices() * snip.getNFrames();
		final double allPixels = imp.getWidth() * imp.getHeight() * imp.getNSlices() * imp.getNFrames();
		final double dt = algo.getProcessingTime();
		final double tmin = Math.ceil( dt * allPixels / snipPixels / 1e3 / 60 ); // min
		gui.setDurationEstimate( tmin );

		// Prepare results holder;
		final ArrayImg< FloatType, FloatArray > F = algo.getGaussianFilteredImage();
		final ArrayImg< FloatType, FloatArray > AD = algo.getAnisotropicDiffusionImage();
		final ArrayImg< FloatType, FloatArray > G = algo.getGradientNorm();
		final ArrayImg< FloatType, FloatArray > L = algo.getLaplacianMagnitude();
		final ArrayImg< FloatType, FloatArray > H = algo.getHessianDeterminant();
		final ArrayImg< FloatType, FloatArray > M = algo.getMask();
		final ArrayImg< FloatType, FloatArray > R = algo.getResult();

		final int width = ( int ) F.dimension( 0 );
		final int height = ( int ) F.dimension( 1 );

		final ImageStack floatStack = new ImageStack( width, height );
		floatStack.addSlice( "Gradient norm", toFloatProcessor( G ) );
		floatStack.addSlice( "Laplacian magnitude", toFloatProcessor( L ) );
		floatStack.addSlice( "Hessian determinant", toFloatProcessor( H ) );
		floatStack.addSlice( "Mask", toFloatProcessor( M ) );
		if ( comp2 == null )
		{
			final ImagePlus imp2 = new ImagePlus( "Scaled derivatives", floatStack );
			comp2 = new CompositeImage( imp2, CompositeImage.GRAYSCALE );
			comp2.setDimensions( floatStack.getSize(), 1, 1 );
			comp2.setOpenAsHyperStack( true );
		}
		else
		{
			comp2.setStack( floatStack, floatStack.getSize(), 1, 1 );
		}
		comp2.show();

		final ImageStack tStack = new ImageStack( width, height );
		tStack.addSlice( "Gaussian filtered", toFloatProcessor( F ) );
		tStack.addSlice( "Anisotropic diffusion", toFloatProcessor( AD ) );
		tStack.addSlice( "Masked image", toFloatProcessor( R ) );
		ImagePlus imp1;
		if ( comp1 == null )
		{
			imp1 = new ImagePlus( "Components", tStack );
			comp1 = new CompositeImage( imp1, CompositeImage.GRAYSCALE );
			comp1.setDimensions( tStack.getSize(), 1, 1 );
			comp1.setOpenAsHyperStack( true );
		}
		else
		{
			comp1.setStack( tStack, tStack.getSize(), 1, 1 );
		}
		comp1.show();

		positionComponentRelativeTo( comp1.getWindow(), imp.getWindow(), 3 );
		positionComponentRelativeTo( comp2.getWindow(), comp1.getWindow(), 2 );
	}

	private static final FloatProcessor toFloatProcessor( final ArrayImg< FloatType, FloatArray > img )
	{
		final FloatArray data = img.update( null );
		final float[] array = data.getCurrentStorageArray();
		final FloatProcessor fp = new FloatProcessor( ( int ) img.dimension( 0 ), ( int ) img.dimension( 1 ), array );
		return fp;
	}

	private void paramStep1Changed()
	{
		// We have to redo all.
		algo.setParameters( gui.getParameters() );
		algo.execStep1();
		algo.execStep2();
		algo.execStep3();
		algo.execStep4();

		final ImageStack s1 = comp1.getStack();
		s1.setPixels( algo.getGaussianFilteredImage().update( null ).getCurrentStorageArray(), 1 );
		s1.setPixels( algo.getAnisotropicDiffusionImage().update( null ).getCurrentStorageArray(), 2 );
		s1.setPixels( algo.getResult().update( null ).getCurrentStorageArray(), 3 );
		comp1.setStack( s1 );

		final ImageStack s2 = comp2.getStack();
		s2.setPixels( algo.getGradientNorm().update( null ).getCurrentStorageArray(), 1 );
		s2.setPixels( algo.getLaplacianMagnitude().update( null ).getCurrentStorageArray(), 2 );
		s2.setPixels( algo.getHessianDeterminant().update( null ).getCurrentStorageArray(), 3 );
		s2.setPixels( algo.getMask().update( null ).getCurrentStorageArray(), 4 );
		comp2.setStack( s2 );
	}

	private void paramStep2Changed()
	{
		algo.setParameters( gui.getParameters() );
		algo.execStep2();
		algo.execStep3();
		algo.execStep4();

		final ImageStack s1 = comp1.getStack();
//		s1.setPixels( algo.getGaussianFilteredImage().update( null ).getCurrentStorageArray(), 1 );
		s1.setPixels( algo.getAnisotropicDiffusionImage().update( null ).getCurrentStorageArray(), 2 );
		s1.setPixels( algo.getResult().update( null ).getCurrentStorageArray(), 3 );
		comp1.setStack( s1 );

		final ImageStack s2 = comp2.getStack();
		s2.setPixels( algo.getGradientNorm().update( null ).getCurrentStorageArray(), 1 );
		s2.setPixels( algo.getLaplacianMagnitude().update( null ).getCurrentStorageArray(), 2 );
		s2.setPixels( algo.getHessianDeterminant().update( null ).getCurrentStorageArray(), 3 );
		s2.setPixels( algo.getMask().update( null ).getCurrentStorageArray(), 4 );
		comp2.setStack( s2 );
	}

	private void paramStep3Changed()
	{
		algo.setParameters( gui.getParameters() );
		algo.execStep3();
		algo.execStep4();

		final ImageStack s1 = comp1.getStack();
//		s1.setPixels( algo.getGaussianFilteredImage().update( null ).getCurrentStorageArray(), 1 );
//		s1.setPixels( algo.getAnisotropicDiffusionImage().update( null ).getCurrentStorageArray(), 2 );
		s1.setPixels( algo.getResult().update( null ).getCurrentStorageArray(), 3 );
		comp1.setStack( s1 );

		final ImageStack s2 = comp2.getStack();
		s2.setPixels( algo.getGradientNorm().update( null ).getCurrentStorageArray(), 1 );
		s2.setPixels( algo.getLaplacianMagnitude().update( null ).getCurrentStorageArray(), 2 );
		s2.setPixels( algo.getHessianDeterminant().update( null ).getCurrentStorageArray(), 3 );
		s2.setPixels( algo.getMask().update( null ).getCurrentStorageArray(), 4 );
		comp2.setStack( s2 );
	}

	private void paramStep4Changed()
	{
		algo.setParameters( gui.getParameters() );
		algo.execStep4();

		final ImageStack s1 = comp1.getStack();
//		s1.setPixels( algo.getGaussianFilteredImage().update( null ).getCurrentStorageArray(), 1 );
//		s1.setPixels( algo.getAnisotropicDiffusionImage().update( null ).getCurrentStorageArray(), 2 );
		s1.setPixels( algo.getResult().update( null ).getCurrentStorageArray(), 3 );
		comp1.setStack( s1 );

		final ImageStack s2 = comp2.getStack();
//		s2.setPixels( algo.getGradientNorm().update( null ).getCurrentStorageArray(), 1 );
//		s2.setPixels( algo.getLaplacianMagnitude().update( null ).getCurrentStorageArray(), 2 );
//		s2.setPixels( algo.getHessianDeterminant().update( null ).getCurrentStorageArray(), 3 );
		s2.setPixels( algo.getMask().update( null ).getCurrentStorageArray(), 4 );
		comp2.setStack( s2 );
	}

	/**
	 * Grab parameters from panel and execute the masking process on the sample
	 * image.
	 */
	private void refresh()
	{
		switch ( stepUpdateToPerform )
		{
		case 1:
			paramStep1Changed();
			break;
		case 2:
			paramStep2Changed();
			break;
		case 3:
			paramStep3Changed();
			break;
		case 4:
			paramStep4Changed();
			break;
		}
		stepUpdateToPerform = Integer.MAX_VALUE;

	}

	@SuppressWarnings( "unused" )
	private static String echoParameters( final double[] param )
	{
		String str = "";
		str += "\n σf\t= " + param[ 0 ];
		str += "\n Nad\t= " + ( int ) param[ 1 ];
		str += "\n κad\t= " + param[ 2 ];
		str += "\n σd\t= " + param[ 3 ];
		str += "\n γ\t= " + param[ 4 ];
		str += "\n α\t= " + param[ 5 ];
		str += "\n β\t= " + param[ 6 ];
		str += "\n ε\t= " + param[ 7 ];
		str += "\n δ\t= " + param[ 8 ];
		return str;
	}

	/*
	 * MAIN METHODS
	 */

	public static void main( final String[] args )
	{

//		File testImage = new File("E:/Users/JeanYves/Documents/Projects/BRajaseka/Data/Meta-nov7mdb18ssplus-embryo2-1.tif");
		final File testImage = new File( "/Users/tinevez/Projects/BRajasekaran/Data/Meta-nov7mdb18ssplus-embryo2-4.tif" );

		ImageJ.main( args );
		final ImagePlus imp = IJ.openImage( testImage.getAbsolutePath() );
		imp.show();

		final CWNT_ plugin = new CWNT_();
		plugin.run( "" );
	}

	/*
	 * PRIVATE CLASS
	 */

	/**
	 * This is a helper class modified after a class by Albert Cardona
	 */
	private class DisplayUpdater extends Thread
	{
		long request = 0;

		// Constructor autostarts thread
		DisplayUpdater()
		{
			super( "CWNT updater thread" );
			setPriority( Thread.NORM_PRIORITY );
			start();
		}

		void doUpdate()
		{
			if ( isInterrupted() )
				return;
			synchronized ( this )
			{
				request++;
				notify();
			}
		}

		void quit()
		{
			interrupt();
			synchronized ( this )
			{
				notify();
			}
		}

		@Override
		public void run()
		{
			while ( !isInterrupted() )
			{
				try
				{
					final long r;
					synchronized ( this )
					{
						r = request;
					}
					// Call displayer update from this thread
					if ( r > 0 )
						refresh();
					synchronized ( this )
					{
						if ( r == request )
						{
							request = 0; // reset
							wait();
						}
						// else loop through to update again
					}
				}
				catch ( final Exception e )
				{
					e.printStackTrace();
				}
			}
		}
	}

	public static void positionComponentRelativeTo( final Component target, final Component anchor, final int direction )
	{

		int x, y;
		switch ( direction )
		{
		case 0:
			x = anchor.getX();
			y = anchor.getY() - target.getHeight();
			break;
		case 1:
		default:
			x = anchor.getX() + anchor.getWidth();
			y = anchor.getY();
			break;
		case 2:
			x = anchor.getX();
			y = anchor.getY() + anchor.getHeight();
			break;
		case 3:
			x = anchor.getX() - target.getWidth();
			y = anchor.getY();
			break;
		}

		// make sure the dialog fits completely on the screen...
		final Dimension sd = Toolkit.getDefaultToolkit().getScreenSize();
		final Rectangle s = new Rectangle( 0, 0, sd.width, sd.height );
		x = Math.min( x, ( s.width - target.getWidth() ) );
		x = Math.max( x, 0 );
		y = Math.min( y, ( s.height - target.getHeight() ) );
		y = Math.max( y, 0 );

		target.setBounds( x + s.x, y + s.y, target.getWidth(), target.getHeight() );

	}
}
