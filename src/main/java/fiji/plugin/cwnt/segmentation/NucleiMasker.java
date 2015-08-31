package fiji.plugin.cwnt.segmentation;

import java.util.List;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import mpicbg.imglib.algorithm.gauss.GaussianFilter2D;
import mpicbg.imglib.algorithm.gauss.GaussianGradient2D;
import net.imglib2.Cursor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.MultiThreadedBenchmarkAlgorithm;
import net.imglib2.algorithm.OutputAlgorithm;
import net.imglib2.algorithm.pde.PeronaMalikAnisotropicDiffusion;
import net.imglib2.algorithm.stats.ComputeMinMax;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.basictypeaccess.array.FloatArray;
import net.imglib2.multithreading.Chunk;
import net.imglib2.multithreading.SimpleMultiThreading;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

@SuppressWarnings( "deprecation" )
public class NucleiMasker< T extends RealType< T > & NativeType< T >> extends MultiThreadedBenchmarkAlgorithm implements OutputAlgorithm< ArrayImg< FloatType, FloatArray >>
{

	private static final boolean DEBUG = true;

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

	/** The factory to create output images. */
	private final ArrayImgFactory< FloatType > imgFactory = new ArrayImgFactory< FloatType >();

	/** The source image (left unchanged). */
	private final RandomAccessibleInterval< T > image;

	/** The target image for the pre-processing steps. */
	private ArrayImg< FloatType, FloatArray > target;

	// Step 1
	private ArrayImg< FloatType, FloatArray > filtered;

	private double gaussFilterSigma = DEFAULT_MASKING_PARAMETERS[ 0 ];

	// Step 2
	private ArrayImg< FloatType, FloatArray > anDiffImage;

	private int nIterAnDiff = ( int ) DEFAULT_MASKING_PARAMETERS[ 1 ];

	private double kappa = DEFAULT_MASKING_PARAMETERS[ 2 ];

	// Step 3
	private double gaussGradSigma = DEFAULT_MASKING_PARAMETERS[ 3 ];

	private ArrayImg< FloatType, FloatArray > Gx;

	private ArrayImg< FloatType, FloatArray > Gy;

	private ArrayImg< FloatType, FloatArray > Gnorm;

	private ArrayImg< FloatType, FloatArray > Gxx;

	private ArrayImg< FloatType, FloatArray > Gxy;

	private ArrayImg< FloatType, FloatArray > Gyx;

	private ArrayImg< FloatType, FloatArray > Gyy;

	private ArrayImg< FloatType, FloatArray > H;

	private ArrayImg< FloatType, FloatArray > L;

	// Step 4
	private ArrayImg< FloatType, FloatArray > M;

	private double gamma = DEFAULT_MASKING_PARAMETERS[ 4 ];

	private double alpha = DEFAULT_MASKING_PARAMETERS[ 5 ];

	private double beta = DEFAULT_MASKING_PARAMETERS[ 6 ];

	private double epsilon = DEFAULT_MASKING_PARAMETERS[ 7 ];

	private double delta = DEFAULT_MASKING_PARAMETERS[ 8 ];

	/*
	 * CONSTRUCTOR
	 */

	public NucleiMasker( final RandomAccessibleInterval< T > image )
	{
		super();
		this.image = image;
	}

	/*
	 * METHODS
	 */

	public ArrayImg< FloatType, FloatArray > getGaussianFilteredImage()
	{
		return filtered;
	}

	public ArrayImg< FloatType, FloatArray > getAnisotropicDiffusionImage()
	{
		return anDiffImage;
	}

	public ArrayImg< FloatType, FloatArray > getGradientNorm()
	{
		return Gnorm;
	}

	public ArrayImg< FloatType, FloatArray > getLaplacianMagnitude()
	{
		return L;
	}

	public ArrayImg< FloatType, FloatArray > getHessianDeterminant()
	{
		return H;
	}

	public ArrayImg< FloatType, FloatArray > getMask()
	{
		return M;
	}

	@Override
	public ArrayImg< FloatType, FloatArray > getResult()
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

	/**
	 * Gaussian filtering of the source image. After this step, the filtered
	 * image is accessible via {@link #getGaussianFilteredImage()}.
	 * 
	 * @return <code>true</code> is processing happened properly.
	 */
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

	/**
	 * Anisotropic diffusion of the filtered image followed by intensity
	 * scaling.
	 * 
	 * @return <code>true</code> is processing happened properly.
	 */
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
		final long top = System.currentTimeMillis();
		final boolean check = execAnisotropicDiffusion();
		if ( !check ) { return false; }
		final long dt = ( System.currentTimeMillis() - top );
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

	@SuppressWarnings( "unchecked" )
	private boolean execMasking()
	{
		target = ( ArrayImg< FloatType, FloatArray > ) imgFactory.create( filtered, new FloatType() );
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

	@SuppressWarnings( "unchecked" )
	private boolean execCreateMask()
	{

		M = ( ArrayImg< FloatType, FloatArray > ) imgFactory.create( Gnorm, new FloatType() );
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

	@SuppressWarnings( "unchecked" )
	private boolean execComputeHessian()
	{
		// "Negative part of Hessian"
		H = ( ArrayImg< FloatType, FloatArray > ) imgFactory.create( Gnorm, new FloatType() );
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
		normalize( H );
		return true;
	}

	@SuppressWarnings( "unchecked" )
	private boolean execComputeLaplacian()
	{

		final GaussianGradient2D< FloatType > gradX = new GaussianGradient2D< FloatType >( Gx, gaussGradSigma );
		gradX.setNumThreads( numThreads );
		boolean check = gradX.checkInput() && gradX.process();
		if ( check )
		{
			final List< ArrayImg< FloatType, FloatArray >> gcX = gradX.getGradientComponents();
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
			final List< ArrayImg< FloatType, FloatArray >> gcY = gradY.getGradientComponents();
			Gyx = gcY.get( 0 );
			Gyy = gcY.get( 1 );
		}
		else
		{
			errorMessage = gradY.getErrorMessage();
			return false;
		}

		// Enucluated laplacian magnitude // "Laplacian positive magnitude"
		L = ( ArrayImg< FloatType, FloatArray > ) imgFactory.create( Gxx, new FloatType() );
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
		normalize( L );
		return true;
	}

	private boolean execComputeGradient()
	{
		final GaussianGradient2D< FloatType > grad = new GaussianGradient2D< FloatType >( anDiffImage, gaussGradSigma );
		grad.setNumThreads( numThreads );
		final boolean check = grad.checkInput() && grad.process();
		if ( check )
		{
			final List< ArrayImg< FloatType, FloatArray >> gc = grad.getGradientComponents();
			Gx = gc.get( 0 );
			Gy = gc.get( 1 );
			Gnorm = grad.getResult();
			normalize( Gnorm );
			return true;
		}
		else
		{
			errorMessage = grad.getErrorMessage();
			return false;
		}

	}

	@SuppressWarnings( "unchecked" )
	private boolean execAnisotropicDiffusion()
	{
		anDiffImage = ( ArrayImg< FloatType, FloatArray > ) filtered.copy();

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
						final PeronaMalikAnisotropicDiffusion< FloatType > andiff;

						if ( anDiffImage.numDimensions() > 2 )
						{
							final IntervalView< FloatType > slice = Views.hyperSlice( anDiffImage, 2, z );
							andiff = new PeronaMalikAnisotropicDiffusion< FloatType >( slice, new ArrayImgFactory< FloatType >(), 0.1429, kappa );
						}
						else
						{
							andiff = new PeronaMalikAnisotropicDiffusion< FloatType >( anDiffImage, new ArrayImgFactory< FloatType >(), 0.1429, kappa );
						}
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

	/**
	 * Normalize the image intensity (in place) so that it ranges between 0 and
	 * 1.
	 * 
	 * @param img
	 *            the image to modify.
	 */
	private static final void normalize( final ArrayImg< FloatType, FloatArray > img )
	{
		final FloatType min = new FloatType();
		final FloatType max = new FloatType();
		ComputeMinMax.computeMinMax( img, min, max );
		final float minf = min.get();
		final float maxf = max.get();
		
		final Cursor< FloatType > cursor = img.cursor( img );
		while ( cursor.hasNext() )
		{
			cursor.fwd();
			final float val = cursor.get().get();
			final float scaled = ( val - minf ) / ( maxf - minf );
			cursor.get().set( scaled );
		}
	}
}
