package mpicbg.imglib.algorithm.gauss;

import ij.process.ImageConverter;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicInteger;

import net.imglib2.algorithm.MultiThreadedBenchmarkAlgorithm;
import net.imglib2.algorithm.OutputAlgorithm;
import net.imglib2.converter.RealFloatConverter;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.multithreading.SimpleMultiThreading;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;


public class GaussianGradient2D <T extends RealType<T>> extends MultiThreadedBenchmarkAlgorithm implements OutputAlgorithm<FloatType> {

	private final Img< T > source;
	private final double sigma;

	private Img< FloatType > Dx;

	private Img< FloatType > Dy;

	private final List< Img< FloatType >> components = new ArrayList< Img< FloatType >>( 2 );


	/*
	 * CONSTRUCTOR
	 */


	public GaussianGradient2D( final Img< T > source, final double sigma )
	{
		super();
		this.source = source;
		this.sigma = sigma;
	}


	/*
	 * METHODS
	 */

	@Override
	public boolean checkInput() {
		return true;
	}

	@Override
	public boolean process() {
		final long start = System.currentTimeMillis();

		// Convert to float; needed to handle negative value properly
		final Img< FloatType > floatImage;
		if ( source.firstElement().getClass().equals( FloatType.class ) )
		{
			final Object tmp = source;
			floatImage = ( Img< FloatType > ) tmp;
		} else {
			final ImageConverter<T, FloatType> converter = new ImageConverter<T, FloatType>(
					source,
					new ImgFactory< FloatType >( new FloatType(), source.factory() ),
					new RealFloatConverter< T >() );
			converter.setNumThreads(numThreads);
			converter.checkInput();
			converter.process();
			floatImage = converter.getResult();
		}

		// In X
		final GaussianConvolutionReal2D<FloatType> gx = new GaussianConvolutionReal2D<FloatType>(
				floatImage,
				new OutOfBoundsStrategyMirrorFactory<FloatType>(),
				new double[] { sigma, sigma} ) {

			@Override
			protected void computeKernel() {

				final double[][] kernel = getKernel();
				kernel[0] = Util.createGaussianKernel1DDouble( GaussianGradient2D.this.sigma, false );
				kernel[1] = Util.createGaussianKernel1DDouble( GaussianGradient2D.this.sigma, true );
				final int kSize = kernel[1].length;
				for (int i = 0; i < kSize; i++) {
					kernel[0][i] = kernel[0][i] * (i - (kSize-1)/2) * 2 / GaussianGradient2D.this.sigma;
				}

			};

		};

		gx.setNumThreads(numThreads);
		boolean check = gx.checkInput() && gx.process();
		if (check) {
			Dx = gx.getResult();
			Dx.setName("Gx " + source.getName());
		} else {
			errorMessage = gx.getErrorMessage();
			return false;
		}

		// In Y
		final GaussianConvolutionReal2D<FloatType> gy = new GaussianConvolutionReal2D<FloatType>(
				floatImage,
				new OutOfBoundsStrategyMirrorFactory<FloatType>(),
				new double[] { sigma, sigma} ) {

			@Override
			protected void computeKernel() {

				final double[][] kernel = getKernel();
				kernel[0] = Util.createGaussianKernel1DDouble( GaussianGradient2D.this.sigma, true );
				kernel[1] = Util.createGaussianKernel1DDouble( GaussianGradient2D.this.sigma, false );
				final int kSize = kernel[0].length;
				for (int i = 0; i < kSize; i++) {
					kernel[1][i] = kernel[1][i] * (i - (kSize-1)/2) * 2 / GaussianGradient2D.this.sigma;
				}

			};

		};

		gy.setNumThreads(numThreads);
		check = gy.checkInput() && gy.process();
		if (check) {
			Dy = gy.getResult();
			Dy.setName("Gy "+source.getName());
		} else {
			errorMessage = gy.getErrorMessage();
			return false;
		}

		components.clear();
		components.add(Dx);
		components.add(Dy);

		final long end = System.currentTimeMillis();
		processingTime = end-start;
		return true;
	}


	public List<Image<FloatType>> getGradientComponents() {
		return components;
	}


	/**
	 * Return the gradient norm
	 */
	@Override
	public Image<FloatType> getResult() {
		final Image<FloatType> norm = Dx.createNewImage("Gradient norm of "+source.getName());

		final Vector<Chunk> chunks = SimpleMultiThreading.divideIntoChunks(norm.getNumPixels(), numThreads);
		final AtomicInteger ai = new AtomicInteger();

		final Thread[] threads = new Thread[chunks.size()];
		for (int i = 0; i < threads.length; i++) {
			threads[i] = new Thread("Gradient norm thread "+i) {
				@Override
				public void run() {

					final Chunk chunk = chunks.get(ai.getAndIncrement());

					final Cursor<FloatType> cx = Dx.createCursor();
					final Cursor<FloatType> cy = Dy.createCursor();
					final Cursor<FloatType> cn = norm.createCursor();

					double x, y;
					cn.fwd(chunk.getStartPosition());
					cx.fwd(chunk.getStartPosition());
					cy.fwd(chunk.getStartPosition());
					for (long j = 0; j < chunk.getLoopSize(); j++) {
						cn.fwd();
						cx.fwd();
						cy.fwd(); // Ok because we have identical containers
						x = cx.getType().get();
						y = cy.getType().get();
						cn.getType().setReal(FastMath.sqrt(x*x+y*y));
					}
					cx.close();
					cy.close();
					cn.close();
				}

			};
		}

		SimpleMultiThreading.startAndJoin(threads);
		return norm;
	}

}
