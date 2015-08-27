package fiji.plugin.cwnt.segmentation;

import java.util.List;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import mpicbg.imglib.algorithm.gauss.GaussianFilter2D;
import mpicbg.imglib.algorithm.gauss.GaussianGradient2D;
import net.imglib2.Cursor;
import net.imglib2.RandomAccess;
import net.imglib2.algorithm.MultiThreadedBenchmarkAlgorithm;
import net.imglib2.algorithm.OutputAlgorithm;
import net.imglib2.algorithm.pde.PeronaMalikAnisotropicDiffusion;
import net.imglib2.exception.IncompatibleTypeException;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.multithreading.Chunk;
import net.imglib2.multithreading.SimpleMultiThreading;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

@SuppressWarnings( "deprecation" )
public class NucleiMasker< T extends RealType< T >> extends MultiThreadedBenchmarkAlgorithm implements OutputAlgorithm< Img< FloatType >>
{

	private static final boolean DEBUG = false;

	/**
	 * A set of default parameters suitable for masking, as determined by Bhavna
	 * Rajasekaran. In the array, the parameters are ordered as follow:
	 * <ol start="0">
	 * <li>the σ for the gaussian filtering in step 1
	 * <li>the number of iteration for anisotropic filtering in step 2
	 * <li>κ, the gradient threshold for anisotropic filtering in step 2
	 * <li>the σ for the gaussian derivatives in step 3
	 * <li>γ, the <i>tanh</i> shift in step 4
	 * <li>α, the gradient prefactor in step 4
	 * <li>β, the laplacian positive magnitude prefactor in step 4
	 * <li>ε, the hessian negative magnitude prefactor in step 4
	 * <li>δ, the derivative sum scale in step 4
	 * </ol>
	 */
	public static final double[] DEFAULT_MASKING_PARAMETERS = new double[] {
			0.5, // 0. σf
			5, // 1. nAD
			50, // 2. κAD
			1, // 3. σg
			1, // 4. γ
			2.7, // 5. α
			14.9, // 6. β
			16.9, // 7. ε
			0.5 // 8. δ
	};

	private static final String BASE_ERROR_MESSAGE = "[NucleiMasker] ";

	/** The source image (left unchanged). */
	private final Img< T > image;

	/** The target image for the pre-processing steps. */
	private Img< FloatType > target;

	// Step 1
	private Img< FloatType > filtered;

	private double gaussFilterSigma = DEFAULT_MASKING_PARAMETERS[ 0 ];

	// Step 2
	private Img< FloatType > anDiffImage;

	private Img< FloatType > scaled;

	private int nIterAnDiff = ( int ) DEFAULT_MASKING_PARAMETERS[ 1 ];

	private double kappa = DEFAULT_MASKING_PARAMETERS[ 2 ];

	// Step 3
	private double gaussGradSigma = DEFAULT_MASKING_PARAMETERS[ 3 ];

	private Img< FloatType > Gx;

	private Img< FloatType > Gy;

	private Img< FloatType > Gnorm;

	private Img< FloatType > Gxx;

	private Img< FloatType > Gxy;

	private Img< FloatType > Gyx;

	private Img< FloatType > Gyy;

	private Img< FloatType > H;

	private Img< FloatType > L;

	// Step 4
	private Img< FloatType > M;

	private double gamma = DEFAULT_MASKING_PARAMETERS[ 4 ];

	private double alpha = DEFAULT_MASKING_PARAMETERS[ 5 ];

	private double beta = DEFAULT_MASKING_PARAMETERS[ 6 ];

	private double epsilon = DEFAULT_MASKING_PARAMETERS[ 7 ];

	private double delta = DEFAULT_MASKING_PARAMETERS[ 8 ];

	/*
	 * CONSTRUCTOR
	 */

	public NucleiMasker( final Img< T > image )
	{
		super();
		this.image = image;
	}

	/*
	 * METHODS
	 */

	public Img< FloatType > getGaussianFilteredImage()
	{
		return filtered;
	}

	public Img< FloatType > getAnisotropicDiffusionImage()
	{
		return anDiffImage;
	}

	public Img< FloatType > getGradientNorm()
	{
		return Gnorm;
	}

	public Img< FloatType > getLaplacianMagnitude()
	{
		return L;
	}

	public Img< FloatType > getHessianDeterminant()
	{
		return H;
	}

	public Img< FloatType > getMask()
	{
		return M;
	}

	@Override
	public Img< FloatType > getResult()
	{
		return target;
	}

	/**
	 * Set the parameters used by this instance to compute the cell mask. In the
	 * array, the parameters must be ordered as follow:
	 * <ol start="0">
	 * <li>the σ for the gaussian filtering in step 1
	 * <li>the number of iteration for anisotropic filtering in step 2
	 * <li>κ, the gradient threshold for anisotropic filtering in step 2
	 * <li>the σ for the gaussian derivatives in step 3
	 * <li>γ, the <i>tanh</i> shift in step 4
	 * <li>α, the gradient prefactor in step 4
	 * <li>β, the laplacian positive magnitude prefactor in step 4
	 * <li>ε, the hessian negative magnitude prefactor in step 4
	 * <li>δ, the derivative sum scale in step 4
	 * </ol>
	 */
	public void setParameters( final double[] params )
	{
		gaussFilterSigma = params[ 0 ];
		nIterAnDiff = ( int ) params[ 1 ];
		kappa = params[ 2 ];
		gaussGradSigma = params[ 3 ];
		gamma = params[ 4 ];
		alpha = params[ 5 ];
		beta = params[ 6 ];
		epsilon = params[ 7 ];
		delta = params[ 8 ];
	}

	public boolean execStep1()
	{
		/*
		 * Step 1: Low pass filter. So as to damper the noise. We simply do a
		 * gaussian filtering.
		 */
		final long top = System.currentTimeMillis();
		boolean check;
		if ( DEBUG )
		{
			System.out.print( String.format( BASE_ERROR_MESSAGE + "Low pass filter, with σf = %.1f ... ", gaussFilterSigma ) );
		}
		check = execGaussianFiltering();
		if ( !check ) { return false; }
		final long dt = ( System.currentTimeMillis() - top );
		processingTime += dt;
		if ( DEBUG )
		{
			System.out.println( "dt = " + dt / 1e3 + " s." );
		}

		return check;
	}

	public boolean execStep2()
	{
		/*
		 * Step 2a: Anisotropic diffusion To have nuclei of approximative
		 * constant intensity.
		 */
		if ( DEBUG )
		{
			System.out.print( String.format( BASE_ERROR_MESSAGE + "Anisotropic diffusion with n = %d and κ = %.1f ... ", nIterAnDiff, kappa ) );
		}
		long top = System.currentTimeMillis();
		boolean check = execAnisotropicDiffusion();
		if ( !check ) { return false; }
		long dt = ( System.currentTimeMillis() - top );
		processingTime += dt;
		if ( DEBUG )
		{
			System.out.println( "dt = " + dt / 1e3 + " s." );
		}

		/*
		 * Step 2b: Intensity scaling Scale intensities in each plane to the
		 * range 0 - 1
		 */
		if ( DEBUG )
		{
			System.out.print( BASE_ERROR_MESSAGE + "Intensity scaling... " );
		}
		top = System.currentTimeMillis();
		check = execIntensityScaling();
		if ( !check ) { return false; }
		dt = ( System.currentTimeMillis() - top );
		processingTime += dt;
		if ( DEBUG )
		{
			System.out.println( "dt = " + dt / 1e3 + " s." );
		}

		return check;
	}

	public boolean execStep3()
	{
		/*
		 * Step 3a: Gaussian gradient
		 */
		if ( DEBUG )
		{
			System.out.print( String.format( BASE_ERROR_MESSAGE + "Gaussian gradient with %.1f ... ", gaussGradSigma ) );
		}
		long top = System.currentTimeMillis();
		boolean check = execComputeGradient();
		if ( !check ) { return false; }
		long dt = ( System.currentTimeMillis() - top );
		processingTime += dt;
		if ( DEBUG )
		{
			System.out.println( "dt = " + dt / 1e3 + " s." );
		}

		/*
		 * Step 3b: Laplacian
		 */
		if ( DEBUG )
		{
			System.out.print( BASE_ERROR_MESSAGE + "Laplacian... " );
		}
		top = System.currentTimeMillis();
		check = execComputeLaplacian();
		if ( !check ) { return false; }
		dt = ( System.currentTimeMillis() - top );
		processingTime += dt;
		if ( DEBUG )
		{
			System.out.println( "dt = " + dt / 1e3 + " s." );
		}

		/*
		 * Step 3c: Hessian
		 */
		if ( DEBUG )
		{
			System.out.print( BASE_ERROR_MESSAGE + "Hessian... " );
		}
		top = System.currentTimeMillis();
		check = execComputeHessian();
		if ( !check ) { return false; }
		dt = ( System.currentTimeMillis() - top );
		processingTime += dt;
		if ( DEBUG )
		{
			System.out.println( "dt = " + dt / 1e3 + " s." );
		}

		return check;
	}

	public boolean execStep4()
	{
		/*
		 * Step 4a: Create masking function
		 */
		if ( DEBUG )
		{
			System.out.print( String.format( BASE_ERROR_MESSAGE + "Creating mask function with γ = %.1f, α = %.1f, β = %.1f, ε = %.1f, δ = %.1f ... ", gamma, alpha, beta, epsilon, delta ) );
		}
		long top = System.currentTimeMillis();
		boolean check = execCreateMask();
		if ( !check ) { return false; }
		long dt = ( System.currentTimeMillis() - top );
		processingTime += dt;
		if ( DEBUG )
		{
			System.out.println( "dt = " + dt / 1e3 + " s." );
		}

		/*
		 * Step 4b: Do masking, with the gaussian filtered image
		 */
		if ( DEBUG )
		{
			System.out.print( BASE_ERROR_MESSAGE + "Masking... " );
		}
		top = System.currentTimeMillis();
		check = execMasking();
		if ( !check ) { return false; }
		dt = ( System.currentTimeMillis() - top );
		processingTime += dt;
		if ( DEBUG )
		{
			System.out.println( "dt = " + dt / 1e3 + " s." );
		}

		return check;
	}

	@Override
	public boolean process()
	{
		boolean check;

		check = execStep1();
		if ( !check ) { return false; }

		check = execStep2();
		if ( !check ) { return false; }

		check = execStep3();
		if ( !check ) { return false; }

		check = execStep4();
		if ( !check ) { return false; }

		return true;

	}

	@Override
	public boolean checkInput()
	{
		return true;
	}

	@Override
	public String getErrorMessage()
	{
		return errorMessage;
	}

	/*
	 * PRIVATE METHODS
	 */

	private boolean execMasking()
	{
		target = filtered.factory().create( filtered, new FloatType() );
		final Vector< Chunk > chunks = SimpleMultiThreading.divideIntoChunks( target.size(), numThreads );
		final AtomicInteger ai = new AtomicInteger();

		final Thread[] threads = SimpleMultiThreading.newThreads( numThreads );
		for ( int i = 0; i < threads.length; i++ )
		{
			threads[ i ] = new Thread( BASE_ERROR_MESSAGE + "Masking thread " + i )
			{
				@Override
				public void run()
				{

					final Chunk chunk = chunks.get( ai.getAndIncrement() );
					final Cursor< FloatType > ct = target.cursor();
					final Cursor< FloatType > cs = filtered.cursor();
					final Cursor< FloatType > cm = M.cursor();

					cm.jumpFwd( chunk.getStartPosition() );
					ct.jumpFwd( chunk.getStartPosition() );
					cs.jumpFwd( chunk.getStartPosition() );

					for ( int j = 0; j < chunk.getLoopSize(); j++ )
					{

						cm.fwd();
						ct.fwd();
						cs.fwd();

						ct.get().setReal( cs.get().getRealDouble() * cm.get().get() );
					}
				}
			};
		}

		SimpleMultiThreading.startAndJoin( threads );
		return true;
	}

	private boolean execCreateMask()
	{

		M = Gnorm.factory().create( Gnorm, new FloatType() );
		final Vector< Chunk > chunks = SimpleMultiThreading.divideIntoChunks( M.size(), numThreads );
		final AtomicInteger ai = new AtomicInteger();

		final Thread[] threads = SimpleMultiThreading.newThreads( numThreads );
		for ( int i = 0; i < threads.length; i++ )
		{
			threads[ i ] = new Thread( BASE_ERROR_MESSAGE + "Create mask thread " + i )
			{
				@Override
				public void run()
				{

					final Chunk chunk = chunks.get( ai.getAndIncrement() );

					final Cursor< FloatType > cm = M.cursor();
					final Cursor< FloatType > cg = Gnorm.cursor();
					final Cursor< FloatType > cl = L.cursor();
					final Cursor< FloatType > ch = H.cursor();

					cm.jumpFwd( chunk.getStartPosition() );
					cg.jumpFwd( chunk.getStartPosition() );
					cl.jumpFwd( chunk.getStartPosition() );
					ch.jumpFwd( chunk.getStartPosition() );

					double m;
					for ( int j = 0; j < chunk.getLoopSize(); j++ )
					{

						cm.fwd();
						cg.fwd();
						cl.fwd();
						ch.fwd();

						m = 0.5 * ( Math.tanh(
								gamma
										- ( alpha * cg.get().get()
												+ beta * cl.get().get()
												+ epsilon * ch.get().get()
										) / delta

								) + 1 );

						cm.get().setReal( m );
					}
				};
			};
		}

		SimpleMultiThreading.startAndJoin( threads );
		return true;
	}

	private boolean execComputeHessian()
	{

		// "Negative part of Hessian"
		H = Gxx.factory().create( Gxx, new FloatType() );
		final Vector< Chunk > chunks = SimpleMultiThreading.divideIntoChunks( H.size(), numThreads );
		final AtomicInteger ai = new AtomicInteger();

		final Thread[] threads = SimpleMultiThreading.newThreads( numThreads );
		for ( int i = 0; i < threads.length; i++ )
		{
			threads[ i ] = new Thread( BASE_ERROR_MESSAGE + "Compute hessian thread " + i )
			{
				@Override
				public void run()
				{

					final Cursor< FloatType > cxx = Gxx.cursor();
					final Cursor< FloatType > cxy = Gxy.cursor();
					final Cursor< FloatType > cyx = Gyx.cursor();
					final Cursor< FloatType > cyy = Gyy.cursor();
					final Cursor< FloatType > ch = H.cursor();

					final Chunk chunk = chunks.get( ai.getAndIncrement() );
					cxx.jumpFwd( chunk.getStartPosition() );
					cxy.jumpFwd( chunk.getStartPosition() );
					cyx.jumpFwd( chunk.getStartPosition() );
					cyy.jumpFwd( chunk.getStartPosition() );
					ch.jumpFwd( chunk.getStartPosition() );
					float h;

					for ( int j = 0; j < chunk.getLoopSize(); j++ )
					{

						ch.fwd();
						cxx.fwd();
						cxy.fwd();
						cyx.fwd();
						cyy.fwd();

						h = ( cxx.get().get() * cyy.get().get() ) - ( cxy.get().get() * cyx.get().get() );
						if ( h < 0 )
						{
							ch.get().set( -h );
						}
					}
				}
			};
		}

		SimpleMultiThreading.startAndJoin( threads );
		return true;
	}

	private boolean execComputeLaplacian()
	{

		final GaussianGradient2D< FloatType > gradX = new GaussianGradient2D< FloatType >( Gx, gaussGradSigma );
		gradX.setNumThreads( numThreads );
		boolean check = gradX.checkInput() && gradX.process();
		if ( check )
		{
			final List< Img< FloatType >> gcX = gradX.getGradientComponents();
			Gxx = gcX.get( 0 );
			Gxy = gcX.get( 1 );
		}
		else
		{
			errorMessage = gradX.getErrorMessage();
			return false;
		}

		final GaussianGradient2D< FloatType > gradY = new GaussianGradient2D< FloatType >( Gy, gaussGradSigma );
		gradY.setNumThreads( numThreads );
		check = gradY.checkInput() && gradY.process();
		if ( check )
		{
			final List< Img< FloatType >> gcY = gradY.getGradientComponents();
			Gyx = gcY.get( 0 );
			Gyy = gcY.get( 1 );
		}
		else
		{
			errorMessage = gradY.getErrorMessage();
			return false;
		}

		// Enucluated laplacian magnitude // "Laplacian positive magnitude"
		L = Gxx.factory().create( Gxx, new FloatType() );
		final Vector< Chunk > chunks = SimpleMultiThreading.divideIntoChunks( L.size(), numThreads );
		final AtomicInteger ai = new AtomicInteger();

		final Thread[] threads = SimpleMultiThreading.newThreads( numThreads );
		for ( int i = 0; i < threads.length; i++ )
		{
			threads[ i ] = new Thread( BASE_ERROR_MESSAGE + "Compute laplacian thread " + i )
			{

				@Override
				public void run()
				{

					final Cursor< FloatType > cxx = Gxx.cursor();
					final Cursor< FloatType > cyy = Gyy.cursor();
					final Cursor< FloatType > cl = L.cursor();

					final Chunk chunk = chunks.get( ai.getAndIncrement() );
					cxx.jumpFwd( chunk.getStartPosition() );
					cyy.jumpFwd( chunk.getStartPosition() );
					cl.jumpFwd( chunk.getStartPosition() );

					float lap;
					for ( int j = 0; j < chunk.getLoopSize(); j++ )
					{
						cl.fwd();
						cxx.fwd();
						cyy.fwd();
						lap = cxx.get().get() + cyy.get().get();
						if ( lap > 0 )
						{
							cl.get().set( lap );
						}
					}
				}
			};
		}

		SimpleMultiThreading.startAndJoin( threads );
		return true;
	}

	private boolean execComputeGradient()
	{
		final GaussianGradient2D< FloatType > grad = new GaussianGradient2D< FloatType >( scaled, gaussGradSigma );
		grad.setNumThreads( numThreads );
		final boolean check = grad.checkInput() && grad.process();
		if ( check )
		{
			final List< Img< FloatType >> gc = grad.getGradientComponents();
			Gx = gc.get( 0 );
			Gy = gc.get( 1 );
			Gnorm = grad.getResult();
			return true;
		}
		else
		{
			errorMessage = grad.getErrorMessage();
			return false;
		}

	}

	private boolean execIntensityScaling()
	{

		ImgFactory< FloatType > factory = null;
		try
		{
			factory = anDiffImage.factory().imgFactory( new FloatType() );
		}
		catch ( final IncompatibleTypeException e )
		{
			e.printStackTrace();
		}
		scaled = factory.create( filtered, new FloatType() );

		final long width = scaled.dimension( 0 );
		final long height = scaled.dimension( 1 );
		final long nslices = scaled.dimension( 2 );

		final AtomicInteger aj = new AtomicInteger();

		final Thread[] threads = SimpleMultiThreading.newThreads( numThreads );

		for ( int i = 0; i < threads.length; i++ )
		{
			threads[ i ] = new Thread( BASE_ERROR_MESSAGE + "Intensity scaling thread " + i )
			{

				@Override
				public void run()
				{

					final RandomAccess< FloatType > cs = anDiffImage.randomAccess( anDiffImage );
					final RandomAccess< FloatType > ct = scaled.randomAccess( scaled );

					float val;
					for ( int z = aj.getAndIncrement(); z < nslices; z = aj.getAndIncrement() )
					{

						if ( nslices > 1 )
						{ // If we get a 2D image
							cs.setPosition( z, 2 );
							ct.setPosition( z, 2 );
						}

						// Find min & max
						final double val_min = anDiffImage.firstElement().getMaxValue();
						final double val_max = anDiffImage.firstElement().getMinValue();
						final FloatType min = anDiffImage.firstElement().createVariable();
						final FloatType max = anDiffImage.firstElement().createVariable();
						min.setReal( val_min );
						max.setReal( val_max );

						for ( int y = 0; y < height; y++ )
						{
							cs.setPosition( y, 1 );

							for ( int x = 0; x < width; x++ )
							{
								cs.setPosition( x, 0 );

								if ( cs.get().compareTo( min ) < 0 )
								{
									min.set( cs.get() );
								}
								if ( cs.get().compareTo( max ) > 0 )
								{
									max.set( cs.get() );
								}

							}
						}

						// Scale
						for ( int y = 0; y < height; y++ )
						{
							cs.setPosition( y, 1 );
							ct.setPosition( y, 1 );

							for ( int x = 0; x < width; x++ )
							{
								cs.setPosition( x, 0 );
								ct.setPosition( x, 0 );

								val = ( cs.get().getRealFloat() - min.getRealFloat() ) / ( max.getRealFloat() - min.getRealFloat() );
								ct.get().set( val );

							}
						}
					}
				}

			};
		}

		SimpleMultiThreading.startAndJoin( threads );
		return true;
	}

	private boolean execAnisotropicDiffusion()
	{
		anDiffImage = filtered.copy();

		final AtomicInteger aj = new AtomicInteger( 0 );
		final AtomicBoolean ok = new AtomicBoolean( true );
		final long nslices = anDiffImage.dimension( 2 );

		final Thread[] threads = SimpleMultiThreading.newThreads( numThreads );

		for ( int i = 0; i < threads.length; i++ )
		{
			threads[ i ] = new Thread( BASE_ERROR_MESSAGE + "Anisotropic diffusion thread " + i )
			{
				@Override
				public void run()
				{
					for ( int z = aj.getAndIncrement(); z < nslices; z = aj.getAndIncrement() )
					{
						final IntervalView< FloatType > slice = Views.hyperSlice( anDiffImage, 2, z );
						final PeronaMalikAnisotropicDiffusion< FloatType > andiff = new PeronaMalikAnisotropicDiffusion< FloatType >( slice, new ArrayImgFactory< FloatType >(), 1, kappa );
						andiff.setNumThreads( 1 );
						boolean check = andiff.checkInput();
						for ( int i = 0; i < nIterAnDiff; i++ )
						{
							check = check && andiff.process();
						}
						if ( !check )
						{
							errorMessage = BASE_ERROR_MESSAGE + andiff.getErrorMessage();
							ok.set( false );
							return;
						}
					}

				}
			};
		}

		SimpleMultiThreading.startAndJoin( threads );
		return ok.get();
	}

	private boolean execGaussianFiltering()
	{
		final double[] sigmas = new double[] { gaussFilterSigma, gaussFilterSigma };
		final GaussianFilter2D< T > gaussFilter = new GaussianFilter2D< T >( image, sigmas );
		gaussFilter.setNumThreads( numThreads );
		final boolean check = gaussFilter.checkInput() && gaussFilter.process();
		target = gaussFilter.getResult();
		filtered = target; // Store for last step.
		return check;
	}

}
