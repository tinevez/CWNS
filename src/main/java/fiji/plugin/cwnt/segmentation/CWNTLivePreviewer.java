package fiji.plugin.cwnt.segmentation;

import ij.ImagePlus;
import ij.ImageStack;
import ij.plugin.Duplicator;
import ij.process.FloatProcessor;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import net.imglib2.img.ImagePlusAdapter;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.real.FloatType;

public class CWNTLivePreviewer extends MouseAdapter implements ActionListener
{

	private final CWNTPanel source;

	private int stepUpdateToPerform = Integer.MAX_VALUE;

	private final DisplayUpdater updater;

	private NucleiMasker< ? > nucleiMasker;

	private ImagePlus comp2;

	private ImagePlus comp1;

	private final ImagePlus imp;

	/*
	 * CONSTRUCTOR
	 */

	public CWNTLivePreviewer( final CWNTPanel panel )
	{
		this.source = panel;
		this.imp = panel.getTargetImagePlus();
		this.updater = new DisplayUpdater();

		source.addActionListener( this );
		imp.getCanvas().addMouseListener( this );

		recomputeSampleWindows( imp );
	}

	/*
	 * PUBLIC METHODS
	 */

	@Override
	public void mouseReleased( final MouseEvent e )
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

	@Override
	public void actionPerformed( final ActionEvent e )
	{
		if ( e == source.STEP1_PARAMETER_CHANGED )
		{
			stepUpdateToPerform = Math.min( 1, stepUpdateToPerform );
			updater.doUpdate();

		}
		else if ( e == source.STEP2_PARAMETER_CHANGED )
		{
			stepUpdateToPerform = Math.min( 2, stepUpdateToPerform );
			updater.doUpdate();

		}
		else if ( e == source.STEP3_PARAMETER_CHANGED )
		{
			stepUpdateToPerform = Math.min( 3, stepUpdateToPerform );
			updater.doUpdate();

		}
		else if ( e == source.STEP4_PARAMETER_CHANGED )
		{
			stepUpdateToPerform = Math.min( 4, stepUpdateToPerform );
			updater.doUpdate();

		}
		else if ( e == source.STEP5_PARAMETER_CHANGED )
		{
			stepUpdateToPerform = Math.min( 5, stepUpdateToPerform );
			updater.doUpdate();

		}
		else
		{
			System.err.println( "Unknwon event caught: " + e );
		}
	}

	void quit()
	{
		updater.quit();
		source.removeActionListener( this );
		imp.getCanvas().removeMouseListener( this );
		comp1.changes = false;
		comp1.close();
		comp2.changes = false;
		comp2.close();
	}

	/*
	 * PRIVATE METHODS
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
		final double[] params = CrownWearingSegmenterFactory.collectMaskingParameters( source.getSettings() );
		nucleiMasker = new NucleiMasker( img );
		nucleiMasker.setParameters( params );
		final boolean check = nucleiMasker.checkInput() && nucleiMasker.process();
		if ( !check )
		{
			System.err.println( "Problem with the segmenter: " + nucleiMasker.getErrorMessage() );
			return;
		}

		final double snipPixels = snip.getWidth() * snip.getHeight() * snip.getNSlices() * snip.getNFrames();
		final double allPixels = imp.getWidth() * imp.getHeight() * imp.getNSlices() * imp.getNFrames();
		final double dt = nucleiMasker.getProcessingTime();
		final int tmin = ( int ) Math.ceil( dt * allPixels / snipPixels / 1e3 / 60 ); // min
		source.labelDurationEstimate.setText( "Total duration rough estimate: " + tmin + " min." );

		// Prepare results holder;
		final Img< FloatType > F = nucleiMasker.getGaussianFilteredImage();
		final Img< FloatType > AD = nucleiMasker.getAnisotropicDiffusionImage();
		final Img< FloatType > G = nucleiMasker.getGradientNorm();
		final Img< FloatType > L = nucleiMasker.getLaplacianMagnitude();
		final Img< FloatType > H = nucleiMasker.getHessianDeterminant();
		final Img< FloatType > M = nucleiMasker.getMask();
		final Img< FloatType > R = nucleiMasker.getResult();

		final double thresholdFactor = ( Double ) source.getSettings().get( CrownWearingSegmenterFactory.THRESHOLD_FACTOR_PARAMETER );
		final OtsuThresholder2D< FloatType > thresholder = new OtsuThresholder2D< FloatType >( R, thresholdFactor );
		thresholder.process();
		final Img< BitType > B = thresholder.getResult();

		final int width = ( int ) F.dimension( 0 );
		final int height = ( int ) F.dimension( 1 );

		final ImageStack floatStack = new ImageStack( width, height );
		floatStack.addSlice( "Gradient norm", toFloatProcessor( G ) );
		floatStack.addSlice( "Laplacian mangitude", toFloatProcessor( L ) );
		floatStack.addSlice( "Hessian determintant", toFloatProcessor( H ) );
		floatStack.addSlice( "Mask", toFloatProcessor( M ) );
		floatStack.addSlice( "Thresholded", toFloatProcessor( B ) );
		if ( comp2 == null )
		{
			comp2 = new ImagePlus( "Scaled derivatives", floatStack );
		}
		else
		{
			comp2.setStack( floatStack );
		}
		comp2.show();
		comp2.getProcessor().resetMinAndMax();

		final ImageStack tStack = new ImageStack( width, height );
		tStack.addSlice( "Gaussian filtered", toFloatProcessor( F ) );
		tStack.addSlice( "Anisotropic diffusion", toFloatProcessor( AD ) );
		tStack.addSlice( "Masked image", toFloatProcessor( R ) );
		if ( comp1 == null )
		{
			comp1 = new ImagePlus( "Components", tStack );
		}
		else
		{
			comp1.setStack( tStack );
		}
		comp1.show();

		positionComponentRelativeTo( comp1.getWindow(), imp.getWindow(), 1 );
		positionComponentRelativeTo( comp2.getWindow(), comp1.getWindow(), 2 );
	}

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
		case 5:
			paramStep5Changed();
			break;
		}
		stepUpdateToPerform = Integer.MAX_VALUE;
	}

	private void paramStep1Changed()
	{
		// We have to redo all.
		final double[] params = CrownWearingSegmenterFactory.collectMaskingParameters( source.getSettings() );
		nucleiMasker.setParameters( params );
		nucleiMasker.execStep1();
		nucleiMasker.execStep2();
		nucleiMasker.execStep3();
		nucleiMasker.execStep4();
		paramStep5Changed();

		final int slice1 = comp1.getSlice();
		comp1.setSlice( 1 );
		comp1.setProcessor( toFloatProcessor( nucleiMasker.getGaussianFilteredImage() ) );
		comp1.setSlice( 2 );
		comp1.setProcessor( toFloatProcessor( nucleiMasker.getAnisotropicDiffusionImage() ) );
		comp1.setSlice( 3 );
		comp1.setProcessor( toFloatProcessor( nucleiMasker.getResult() ) );
		comp1.setSlice( slice1 );

		final int slice2 = comp2.getSlice();
		comp2.setSlice( 1 );
		comp2.setProcessor( toFloatProcessor( nucleiMasker.getGradientNorm() ) );
		comp2.setSlice( 2 );
		comp2.setProcessor( toFloatProcessor( nucleiMasker.getLaplacianMagnitude() ) );
		comp2.setSlice( 3 );
		comp2.setProcessor( toFloatProcessor( nucleiMasker.getHessianDeterminant() ) );
		comp2.setSlice( 4 );
		comp2.setProcessor( toFloatProcessor( nucleiMasker.getMask() ) );
		comp2.setSlice( slice2 );
		comp2.getProcessor().setMinAndMax( 0, 2 );
	}

	private void paramStep2Changed()
	{
		final double[] params = CrownWearingSegmenterFactory.collectMaskingParameters( source.getSettings() );
		nucleiMasker.setParameters( params );
		nucleiMasker.execStep2();
		nucleiMasker.execStep3();
		nucleiMasker.execStep4();

		final int slice1 = comp1.getSlice();
		comp1.setSlice( 2 );
		comp1.setProcessor( toFloatProcessor( nucleiMasker.getAnisotropicDiffusionImage() ) );
		comp1.setSlice( 3 );
		comp1.setProcessor( toFloatProcessor( nucleiMasker.getResult() ) );
		comp1.setSlice( slice1 );
		paramStep5Changed();

		final int slice2 = comp2.getSlice();
		comp2.setSlice( 1 );
		comp2.setProcessor( toFloatProcessor( nucleiMasker.getGradientNorm() ) );
		comp2.setSlice( 2 );
		comp2.setProcessor( toFloatProcessor( nucleiMasker.getLaplacianMagnitude() ) );
		comp2.setSlice( 3 );
		comp2.setProcessor( toFloatProcessor( nucleiMasker.getHessianDeterminant() ) );
		comp2.setSlice( 4 );
		comp2.setProcessor( toFloatProcessor( nucleiMasker.getMask() ) );
		comp2.setSlice( slice2 );
		comp2.getProcessor().setMinAndMax( 0, 2 );
	}

	private void paramStep3Changed()
	{
		final double[] params = CrownWearingSegmenterFactory.collectMaskingParameters( source.getSettings() );
		nucleiMasker.setParameters( params );
		nucleiMasker.execStep3();
		nucleiMasker.execStep4();
		paramStep5Changed();

		final int slice1 = comp1.getSlice();
		comp1.setSlice( 3 );
		comp1.setProcessor( toFloatProcessor( nucleiMasker.getResult() ) );
		comp1.setSlice( slice1 );

		final int slice2 = comp2.getSlice();
		comp2.setSlice( 1 );
		comp2.setProcessor( toFloatProcessor( nucleiMasker.getGradientNorm() ) );
		comp2.setSlice( 2 );
		comp2.setProcessor( toFloatProcessor( nucleiMasker.getLaplacianMagnitude() ) );
		comp2.setSlice( 3 );
		comp2.setProcessor( toFloatProcessor( nucleiMasker.getHessianDeterminant() ) );
		comp2.setSlice( 4 );
		comp2.setProcessor( toFloatProcessor( nucleiMasker.getMask() ) );
		comp2.setSlice( slice2 );
		comp2.getProcessor().setMinAndMax( 0, 2 );
	}

	private void paramStep4Changed()
	{
		final double[] params = CrownWearingSegmenterFactory.collectMaskingParameters( source.getSettings() );
		nucleiMasker.setParameters( params );
		nucleiMasker.execStep4();
		paramStep5Changed();

		final int slice1 = comp1.getSlice();
		comp1.setSlice( 3 );
		comp1.setProcessor( toFloatProcessor( nucleiMasker.getResult() ) );
		comp1.setSlice( slice1 );

		final int slice2 = comp2.getSlice();
		comp2.setSlice( 4 );
		comp2.setProcessor( toFloatProcessor( nucleiMasker.getMask() ) );
		comp2.setSlice( slice2 );
		comp2.getProcessor().setMinAndMax( 0, 2 );
	}

	private void paramStep5Changed()
	{
		final double threshFact = ( Double ) source.getSettings().get( CrownWearingSegmenterFactory.THRESHOLD_FACTOR_PARAMETER );
		final Img< FloatType > img = nucleiMasker.getResult();
		final OtsuThresholder2D< FloatType > thresholder = new OtsuThresholder2D< FloatType >( img, threshFact );
		thresholder.process();
		final Img< BitType > bit = thresholder.getResult();

		final int slice2 = comp2.getSlice();
		comp2.setSlice( 5 );
		comp2.setProcessor( toFloatProcessor( bit ) );
		comp2.setSlice( slice2 );
		comp2.getProcessor().setMinAndMax( 0, 2 );
	}

	@SuppressWarnings( { "rawtypes", "unchecked" } )
	private FloatProcessor toFloatProcessor( final Img img )
	{
		final ImagePlus wrapFloat = ImageJFunctions.wrapFloat( img, "Wrapped" );
		final FloatProcessor fip = ( FloatProcessor ) wrapFloat.getProcessor();
		fip.resetMinAndMax();
		return fip;
	}

	/*
	 * STATIC METHODS
	 */

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
}
