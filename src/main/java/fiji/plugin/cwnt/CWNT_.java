package fiji.plugin.cwnt;

import fiji.plugin.cwnt.gui.CwntGui;
import fiji.plugin.cwnt.segmentation.CrownWearingSegmenter;
import fiji.plugin.cwnt.segmentation.NucleiMasker;
import fiji.plugin.trackmate.Logger;
import fiji.plugin.trackmate.Settings;
import fiji.plugin.trackmate.Spot;
import fiji.plugin.trackmate.SpotCollection;
import fiji.plugin.trackmate.TrackMateModel;
import fiji.plugin.trackmate.io.TmXmlWriter;
import fiji.plugin.trackmate.segmentation.SegmenterSettings;
import fiji.plugin.trackmate.tracking.FastLAPTracker;
import fiji.plugin.trackmate.tracking.LAPTrackerSettings;
import fiji.plugin.trackmate.util.TMUtils;
import fiji.plugin.trackmate.visualization.TrackMateModelView;
import fiji.plugin.trackmate.visualization.hyperstack.HyperStackDisplayer;
import fiji.plugin.trackmate.visualization.hyperstack.SpotOverlay;
import fiji.plugin.trackmate.visualization.hyperstack.TrackOverlay;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.plugin.Duplicator;
import ij.plugin.PlugIn;
import ij.process.FloatProcessor;

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

import javax.swing.JFrame;

import mpicbg.imglib.image.Image;
import mpicbg.imglib.image.ImagePlusAdapter;
import mpicbg.imglib.image.display.imagej.ImageJVirtualStack;
import mpicbg.imglib.type.numeric.IntegerType;
import mpicbg.imglib.type.numeric.real.FloatType;

public class CWNT_ implements PlugIn {

	/*
	 * FIELDS
	 */

	private NucleiMasker<? extends IntegerType<?>> algo;
	private ImagePlus comp2;
	private ImagePlus comp1;
	private CwntGui gui;
	public static final String PLUGIN_NAME = "Crown-Wearing Nuclei Tracker ß2";
	private int stepUpdateToPerform = Integer.MAX_VALUE;
	private DisplayUpdater updater = new DisplayUpdater();
	private TrackMateModel model;
	private Logger logger;
	private HyperStackDisplayer view;



	@Override
	public void run(String arg) {

		// Get current image sample
		final ImagePlus imp = WindowManager.getCurrentImage();
		if (null == imp)
			return;

		// Create Panel silently
		gui = new CwntGui();
		logger = Logger.DEFAULT_LOGGER;

		// Add listeners
		imp.getCanvas().addMouseListener(new MouseAdapter() {

			@Override
			public void mouseReleased(MouseEvent e) {
				if (gui.getSelectedIndex() == gui.indexPanelParameters2 || gui.getSelectedIndex() == gui.indexPanelParameters1) {
					new Thread("CWNT tuning thread") {
						public void run() {
							recomputeSampleWindows(imp);
						};
					}.start();
				}
			}
		});

		
		JFrame frame = new JFrame(PLUGIN_NAME);
		frame.setSize(300, 400);
		frame.addWindowListener(new WindowListener() {
			public void windowOpened(WindowEvent e) { }
			public void windowIconified(WindowEvent e) { }
			public void windowDeiconified(WindowEvent e) { }
			public void windowDeactivated(WindowEvent e) { }
			public void windowClosing(WindowEvent e) { }
			public void windowClosed(WindowEvent e) { updater.quit(); }
			public void windowActivated(WindowEvent e) {}
		});

		gui.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {

				if (e == gui.STEP1_PARAMETER_CHANGED) {
					stepUpdateToPerform = Math.min(1, stepUpdateToPerform);
					updater.doUpdate();

				} else if (e == gui.STEP2_PARAMETER_CHANGED) {
					stepUpdateToPerform = Math.min(2, stepUpdateToPerform);
					updater.doUpdate();

				} else if (e == gui.STEP3_PARAMETER_CHANGED) {
					stepUpdateToPerform = Math.min(3, stepUpdateToPerform);
					updater.doUpdate();

				} else if (e == gui.STEP4_PARAMETER_CHANGED) {
					stepUpdateToPerform = Math.min(4, stepUpdateToPerform);
					updater.doUpdate();

				} else if (e == gui.TAB_CHANGED) {

					if (comp1 == null && comp2 == null && (gui.getSelectedIndex() == gui.indexPanelParameters2 || gui.getSelectedIndex() == gui.indexPanelParameters1)) {
						new Thread("CWNT tuning thread") {
							public void run() {
								recomputeSampleWindows(imp);
							};
						}.start();
					}

				} else if  (e == gui.GO_BUTTON_PRESSED) {
					new Thread("CWNT computation thread") {
						public void run() {
							gui.btnGo.setEnabled(false);
							try {
								process(imp);
							} 	finally {
								gui.btnGo.setEnabled(true);
							}
						};
					}.start();


				} else {
					System.err.println("Unknwon event caught: "+e);
				}
			}
		});

		frame.getContentPane().add(gui);
		frame.setVisible(true);
	}
	
	
	
	/*
	 * BATCH PROCESS METHODS
	 */

	public void process(final ImagePlus imp) {
		
		SimpleDateFormat ft = new SimpleDateFormat ("yyyy-MM-dd 'at' HH:mm:ss");
		Date dNow = new Date( );
		logger.log("Crown-Wearing Nuclei Tracker\n");
		logger.log("----------------------------\n");
		logger.log(ft.format(dNow)+"\n");

		TrackMateModel model = execSegmentation(imp);
		launchDisplayer(model);
		execTracking(model);
		saveResults(model);
		gui.setModelAndView(model, view);
		
		logger.setStatus("");
		logger.setProgress(0f);
		dNow = new Date( );
		logger.log("CWNT process finished.\n");
		logger.log(ft.format(dNow)+"\n");
		logger.log("----------------------------\n");

	}
	
	private void saveResults(TrackMateModel model) {
		
		ImagePlus imp = model.getSettings().imp;
		String dir = imp.getOriginalFileInfo().directory;
		String name = imp.getOriginalFileInfo().fileName;
		name = TMUtils.renameFileExtension(name, "xml");
		File file = new File(dir, name);
		
		logger.log("Saving to file "+file.getAbsolutePath()+"...\n");
		TmXmlWriter writer = new TmXmlWriter(model, logger);
		writer.appendBasicSettings();
		writer.appendSegmenterSettings();
		writer.appendTrackerSettings();
		writer.appendInitialSpotFilter();
		writer.appendSpotFilters();
		writer.appendFilteredSpots();
		writer.appendTracks();
		writer.appendSpots();
		try {
			writer.writeToFile(file);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		logger.log("Saving done.\n");
	}
	
	private void execTracking(TrackMateModel model) {
		
		// Prepare tracking settings
		LAPTrackerSettings ts = new LAPTrackerSettings();
		ts.allowGapClosing 	= false;
		ts.gapClosingFeaturePenalties.clear();
		ts.allowMerging 	= false;
		ts.allowSplitting 	= false;
		
		// Evaluate max dist
		logger.log("Evaluating max linking distance...\n");
		double maxDist = 0;
		for (Spot spot : model.getSpots().getAllSpots()) {
			maxDist += spot.getFeature(Spot.RADIUS);
		}
		maxDist /= model.getSpots().getNSpots();
		maxDist *= 2;
		logger.log(String.format("Max linking distance evaluated to %.1f %s.\n", maxDist, model.getSettings().spaceUnits));
		
		ts.linkingDistanceCutOff = maxDist;
		ts.gapClosingDistanceCutoff = 2 * maxDist;
		ts.gapClosingTimeCutoff = 2 * model.getSettings().dt;
		model.getSettings().trackerSettings = ts;
		
		
		logger.log("Performing track linking...\n");
		logger.setStatus("Tracking...");
		
		FastLAPTracker tracker = new FastLAPTracker();
		tracker.setModel(model);
		tracker.setLogger(logger);
		tracker.setNumThreads(1); // For memory preservation
		if (!(tracker.checkInput() && tracker.process())) {
			logger.error(tracker.getErrorMessage());
			return;
		}
		
		model.getSettings().trackerSettings = ts;
		model.setGraph(tracker.getResult());
		logger.log(String.format("Track linking completed in %.1f s.\n", (tracker.getProcessingTime()/1e3)));
		logger.log(String.format("Found %d tracks.\n", model.getNTracks()));
	}


	@SuppressWarnings({ "unchecked", "rawtypes" })
	private TrackMateModel execSegmentation(ImagePlus imp) {

		Duplicator duplicator = new Duplicator();
		ImagePlus frame;

		// Build (dummy) settings object. Required for spot translation and saving/loading
		final Settings settings = new Settings(imp); // will set the crop rectangle
		settings.imageFileName = imp.getOriginalFileInfo().fileName;
		settings.imageFolder = "";
		settings.spaceUnits = imp.getCalibration().getUnit();
		settings.timeUnits = imp.getCalibration().getTimeUnit();
		
		if (settings.dt == 0) {
			settings.dt = 1;
			settings.timeUnits = "frame";
		}
		
		final float[] calibration = settings.getCalibration();
		final SpotCollection allSpots = new SpotCollection();

		logger.log(settings.toString());
		logger.setStatus("Segmenting...");
		for (int i = 0; i < imp.getNFrames(); i++) {

			frame = duplicator.run(imp, imp.getChannel(), imp.getChannel(), 1, imp.getNSlices(), i+1, i+1);

			// Copy to Imglib
			Image<? extends IntegerType<?>> img = null;
			switch( imp.getType() )	{		
			case ImagePlus.GRAY8 : 
				img =  ImagePlusAdapter.wrapByte( frame );
				break;
			case ImagePlus.GRAY16 : 
				img = ImagePlusAdapter.wrapShort( frame );
				break;
			default:
				logger.error("Image type not handled: "+imp.getType());
				return null;
			}

			// Instantiate segmenter
			CrownWearingSegmenter segmenter = new CrownWearingSegmenter();
			SegmenterSettings ss = gui.getSegmenterSettings();
			segmenter.setTarget(img, calibration, ss);
			settings.segmenterSettings = ss;
			
			// Exec
			if (!(segmenter.checkInput() && segmenter.process())) {
				logger.error("Problem with segmenter: "+segmenter.getErrorMessage());
				return null;
			}
			List<Spot> spots = segmenter.getResult();
			
			TMUtils.translateSpots(spots, 
					settings.xstart * calibration[0], 
					settings.ystart * calibration[1], 
					settings.zstart * calibration[2]);
			
			// Tune time features
			final float t = (float) (i * settings.dt);
			for(Spot spot : spots) {
				spot.putFeature(Spot.POSITION_T, t);
			}	
			
			allSpots.put(i, spots);
			logger.setProgress((float) (i+1) / imp.getNFrames());
			logger.log(String.format("Frame %3d: found %d nuclei in %.1f s.\n",
					(i+1), spots.size(), (segmenter.getProcessingTime()/1e3)));
		}

		logger.setProgress(0);
		logger.setStatus("");
		
		model = new TrackMateModel();
		model.setSettings(settings);
		model.setSpots(allSpots, false);
		model.setInitialSpotFilterValue(0f);
		model.setFilteredSpots(allSpots, false);

		return model;

	}


	private void launchDisplayer(TrackMateModel model) {
		view = createLocalSliceDisplayer(); 
		logger.log("Rendering segmentation results...\n");
		view.setModel(model);
		view.setDisplaySettings(TrackMateModelView.KEY_TRACK_DISPLAY_MODE, TrackMateModelView.TRACK_DISPLAY_MODE_LOCAL_QUICK);
		view.render();
		logger.log("Rendering done.\n");

	}
	
	public static HyperStackDisplayer createLocalSliceDisplayer() {
		
		return new HyperStackDisplayer() {
			
			@Override
			protected SpotOverlay createSpotOverlay() {
				
				return new SpotOverlay(model, model.getSettings().imp, displaySettings) {
					
					public void drawSpot(final Graphics2D g2d, final Spot spot, final float zslice, 
							final int xcorner, final int ycorner, final float magnification) {

						final float x = spot.getFeature(Spot.POSITION_X);
						final float y = spot.getFeature(Spot.POSITION_Y);
						final float z = spot.getFeature(Spot.POSITION_Z);
						final float dz2 = (z - zslice) * (z - zslice);
						float radiusRatio = (Float) displaySettings.get(TrackMateModelView.KEY_SPOT_RADIUS_RATIO);
						final float radius = spot.getFeature(Spot.RADIUS)*radiusRatio;
						if (dz2 >= radius * radius)
							return;

						// In pixel units
						final float xp = x / calibration[0];
						final float yp = y / calibration[1];
						// Scale to image zoom
						final float xs = (xp - xcorner) * magnification ;
						final float ys = (yp - ycorner) * magnification ;

						final float apparentRadius =  (float) (Math.sqrt(radius*radius - dz2) / calibration[0] * magnification); 
						g2d.drawOval(Math.round(xs - apparentRadius), Math.round(ys - apparentRadius), 
								Math.round(2 * apparentRadius), Math.round(2 * apparentRadius));		
						boolean spotNameVisible = (Boolean) displaySettings.get(TrackMateModelView.KEY_DISPLAY_SPOT_NAMES);
						if (spotNameVisible ) {
							String str = spot.toString();
							int xindent = fm.stringWidth(str) / 2;
							int yindent = fm.getAscent() / 2;
							g2d.drawString(spot.toString(), xs-xindent, ys+yindent);
						}
					}
				};
			}
			
			@Override
			protected TrackOverlay createTrackOverlay() {
			
				return new TrackOverlay(model, model.getSettings().imp, displaySettings) {
					
					@Override
					protected void drawEdge(Graphics2D g2d, Spot source, Spot target, int xcorner, int ycorner,	float magnification) {
						// Find x & y in physical coordinates
						final float x0i = source.getFeature(Spot.POSITION_X);
						final float y0i = source.getFeature(Spot.POSITION_Y);
						final float z0i = source.getFeature(Spot.POSITION_Z);
						final float x1i = target.getFeature(Spot.POSITION_X);
						final float y1i = target.getFeature(Spot.POSITION_Y);
						final float z1i = target.getFeature(Spot.POSITION_Z);
						// In pixel units
						final float x0p = x0i / calibration[0];
						final float y0p = y0i / calibration[1];
						final float z0p = z0i / calibration[2];
						final float x1p = x1i / calibration[0];
						final float y1p = y1i / calibration[1];
						final float z1p = z1i / calibration[2];
						// Check if we are nearing their plane
						final int czp = (imp.getSlice()-1);
						if (Math.abs(czp-z1p) > 3 && Math.abs(czp-z0p) > 3) {
							return;
						}
						// Scale to image zoom
						final float x0s = (x0p - xcorner) * magnification ;
						final float y0s = (y0p - ycorner) * magnification ;
						final float x1s = (x1p - xcorner) * magnification ;
						final float y1s = (y1p - ycorner) * magnification ;
						// Round
						final int x0 = Math.round(x0s);
						final int y0 = Math.round(y0s);
						final int x1 = Math.round(x1s);
						final int y1 = Math.round(y1s);

						g2d.drawLine(x0, y0, x1, y1);
					}
				};
				
			}
			
		};
		
	}

	
	
	/*
	 * PREVIEW METHODS
	 */

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private void recomputeSampleWindows(ImagePlus imp) {

		ImagePlus snip = new Duplicator().run(imp, imp.getSlice(), imp.getSlice());

		// Copy to Imglib
		Image<? extends IntegerType<?>> img = null;
		switch( imp.getType() )	{		
		case ImagePlus.GRAY8 : 
			img =  ImagePlusAdapter.wrapByte( snip );
			break;
		case ImagePlus.GRAY16 : 
			img = ImagePlusAdapter.wrapShort( snip );
			break;
		default:
			System.err.println("Image type not handled: "+imp.getType());
			return;
		}

		// Prepare algo
		algo = new NucleiMasker(img);
		algo.setParameters(gui.getParameters());
		boolean check = algo.checkInput() && algo.process();
		if (!check) {
			System.err.println("Problem with the segmenter: "+algo.getErrorMessage());
			return;
		}

		double snipPixels = snip.getWidth() * snip.getHeight() * snip.getNSlices() * snip.getNFrames();
		double allPixels = imp.getWidth() * imp.getHeight() * imp.getNSlices() * imp.getNFrames();
		double dt = algo.getProcessingTime();
		double tmin = Math.ceil(dt * allPixels / snipPixels / 1e3 / 60); //min 
		gui.setDurationEstimate(tmin);

		// Prepare results holder;
		Image 				F 	= algo.getGaussianFilteredImage();
		Image 				AD	= algo.getAnisotropicDiffusionImage();
		Image<FloatType> 	G 	= algo.getGradientNorm();
		Image<FloatType> 	L 	= algo.getLaplacianMagnitude();
		Image<FloatType> 	H 	= algo.getHessianDeterminant();
		Image<FloatType> 	M 	= algo.getMask();
		Image 				R 	= algo.getResult();

		int width = F.getDimension(0);
		int height = F.getDimension(1);

		ImageStack floatStack = new ImageStack(width, height); // The stack of ips that scales roughly from 0 to 1
		floatStack.addSlice("Gradient norm", toFloatProcessor(G));
		floatStack.addSlice("Laplacian mangitude", toFloatProcessor(L));
		floatStack.addSlice("Hessian determintant", toFloatProcessor(H));
		floatStack.addSlice("Mask", toFloatProcessor(M));
		if (comp2 == null) {
			comp2 = new ImagePlus("Scaled derivatives", floatStack);
		} else {
			comp2.setStack(floatStack); 
		}
		comp2.show();
		comp2.getProcessor().resetMinAndMax();

		ImageStack tStack = new ImageStack(width, height); // The stack of ips that scales roughly like source image
		tStack.addSlice("Gaussian filtered", toFloatProcessor(F));
		tStack.addSlice("Anisotropic diffusion", toFloatProcessor(AD));
		tStack.addSlice("Masked image", toFloatProcessor(R));
		if (comp1 == null) {
			comp1 = new ImagePlus("Components", tStack);
		} else {
			comp1.setStack(tStack);
		}
		comp1.show();

		positionComponentRelativeTo(comp1.getWindow(), imp.getWindow(), 3);
		positionComponentRelativeTo(comp2.getWindow(), comp1.getWindow(), 2);
	}




	private FloatProcessor toFloatProcessor(@SuppressWarnings("rawtypes") Image img) {
		@SuppressWarnings("unchecked")
		FloatProcessor fip = new FloatProcessor( img.getDimension(0), img.getDimension(1), 
				ImageJVirtualStack.extractSliceFloat( img, img.getDisplay(), 0, 1, new int[3] ), null); 
		fip.setMinAndMax( img.getDisplay().getMin(),  img.getDisplay().getMax() );
		return fip;
	}


	private void paramStep1Changed() {
		// We have to redo all.
		algo.setParameters(gui.getParameters());
		algo.execStep1(); 
		algo.execStep2(); 
		algo.execStep3(); 
		algo.execStep4();

		int slice1 = comp1.getSlice();
		comp1.setSlice(1);
		comp1.setProcessor(toFloatProcessor(algo.getGaussianFilteredImage()));
		comp1.setSlice(2);
		comp1.setProcessor(toFloatProcessor(algo.getAnisotropicDiffusionImage()));
		comp1.setSlice(3);
		comp1.setProcessor(toFloatProcessor(algo.getResult()));
		comp1.setSlice(slice1);

		int slice2 = comp2.getSlice();
		comp2.setSlice(1);
		comp2.setProcessor(toFloatProcessor(algo.getGradientNorm()));
		comp2.setSlice(2);
		comp2.setProcessor(toFloatProcessor(algo.getLaplacianMagnitude()));
		comp2.setSlice(3);
		comp2.setProcessor(toFloatProcessor(algo.getHessianDeterminant()));
		comp2.setSlice(4);
		comp2.setProcessor(toFloatProcessor(algo.getMask()));
		comp2.setSlice(slice2);
		comp2.getProcessor().setMinAndMax(0, 2);
	}

	private void paramStep2Changed() {
		algo.setParameters(gui.getParameters());
		algo.execStep2(); 
		algo.execStep3(); 
		algo.execStep4(); 

		int slice1 = comp1.getSlice();
		comp1.setSlice(2);
		comp1.setProcessor(toFloatProcessor(algo.getAnisotropicDiffusionImage()));
		comp1.setSlice(3);
		comp1.setProcessor(toFloatProcessor(algo.getResult()));
		comp1.setSlice(slice1);

		int slice2 = comp2.getSlice();
		comp2.setSlice(1);
		comp2.setProcessor(toFloatProcessor(algo.getGradientNorm()));
		comp2.setSlice(2);
		comp2.setProcessor(toFloatProcessor(algo.getLaplacianMagnitude()));
		comp2.setSlice(3);
		comp2.setProcessor(toFloatProcessor(algo.getHessianDeterminant()));
		comp2.setSlice(4);
		comp2.setProcessor(toFloatProcessor(algo.getMask()));
		comp2.setSlice(slice2);
		comp2.getProcessor().setMinAndMax(0, 2);
	}

	private void paramStep3Changed() {
		algo.setParameters(gui.getParameters());
		algo.execStep3(); 
		algo.execStep4(); 

		int slice1 = comp1.getSlice();
		comp1.setSlice(3);
		comp1.setProcessor(toFloatProcessor(algo.getResult()));
		comp1.setSlice(slice1);

		int slice2 = comp2.getSlice();
		comp2.setSlice(1);
		comp2.setProcessor(toFloatProcessor(algo.getGradientNorm()));
		comp2.setSlice(2);
		comp2.setProcessor(toFloatProcessor(algo.getLaplacianMagnitude()));
		comp2.setSlice(3);
		comp2.setProcessor(toFloatProcessor(algo.getHessianDeterminant()));
		comp2.setSlice(4);
		comp2.setProcessor(toFloatProcessor(algo.getMask()));
		comp2.setSlice(slice2);
		comp2.getProcessor().setMinAndMax(0, 2);
	}

	private void paramStep4Changed() {
		algo.setParameters(gui.getParameters());
		algo.execStep4(); 

		int slice1 = comp1.getSlice();
		comp1.setSlice(3);
		comp1.setProcessor(toFloatProcessor(algo.getResult()));
		comp1.setSlice(slice1);

		int slice2 = comp2.getSlice();
		comp2.setSlice(4);
		comp2.setProcessor(toFloatProcessor(algo.getMask()));
		comp2.setSlice(slice2);
		comp2.getProcessor().setMinAndMax(0, 2);
	}


	/**
	 * Grab parameters from panel and execute the masking process on the sample image.
	 */
	private void refresh() {
		switch (stepUpdateToPerform) {
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


	@SuppressWarnings("unused")
	private static String echoParameters(final double[] param) {
		String str = "";
		str 	+= "\n σf\t= " + param[0];
		str 	+= "\n Nad\t= " + (int) param[1];
		str 	+= "\n κad\t= " + param[2];
		str 	+= "\n σd\t= " + param[3];
		str 	+= "\n γ\t= " + param[4];
		str 	+= "\n α\t= " + param[5];
		str 	+= "\n β\t= " + param[6];
		str 	+= "\n ε\t= " + param[7];
		str 	+= "\n δ\t= " + param[8];
		return str;
	}


	/*
	 * MAIN METHODS
	 */

	public static void main(String[] args) {

//		File testImage = new File("E:/Users/JeanYves/Documents/Projects/BRajaseka/Data/Meta-nov7mdb18ssplus-embryo2-1.tif");
		File testImage = new File("/Users/tinevez/Projects/BRajasekaran/Data/Meta-nov7mdb18ssplus-embryo2-2.tif");

		ImageJ.main(args);
		ImagePlus imp = IJ.openImage(testImage.getAbsolutePath());
		imp.show();

		CWNT_ plugin = new CWNT_();
		plugin.run("");
	}


	/*
	 * PRIVATE CLASS
	 */

	/**
	 * This is a helper class modified after a class by Albert Cardona
	 */
	private class DisplayUpdater extends Thread {
		long request = 0;

		// Constructor autostarts thread
		DisplayUpdater() {
			super("CWNT updater thread");
			setPriority(Thread.NORM_PRIORITY);
			start();
		}

		void doUpdate() {
			if (isInterrupted())
				return;
			synchronized (this) {
				request++;
				notify();
			}
		}

		void quit() {
			interrupt();
			synchronized (this) {
				notify();
			}
		}

		public void run() {
			while (!isInterrupted()) {
				try {
					final long r;
					synchronized (this) {
						r = request;
					}
					// Call displayer update from this thread
					if (r > 0)
						refresh();
					synchronized (this) {
						if (r == request) {
							request = 0; // reset
							wait();
						}
						// else loop through to update again
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}

	public static void positionComponentRelativeTo(final Component target, final Component anchor, final int direction) {

		int x, y;
		switch (direction) {
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
		final Rectangle s =  new Rectangle (0, 0, sd.width, sd.height);
		x = Math.min(x, (s.width - target.getWidth()));
		x = Math.max(x, 0);
		y = Math.min(y, (s.height - target.getHeight()));
		y = Math.max(y, 0);

		target.setBounds(x + s.x, y + s.y, target.getWidth(), target.getHeight());

	}

}
