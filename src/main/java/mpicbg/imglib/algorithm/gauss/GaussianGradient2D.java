package mpicbg.imglib.algorithm.gauss;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.math.util.FastMath;

import mpicbg.imglib.algorithm.MultiThreadedBenchmarkAlgorithm;
import mpicbg.imglib.algorithm.OutputAlgorithm;
import mpicbg.imglib.algorithm.math.ImageConverter;
import mpicbg.imglib.cursor.Cursor;
import mpicbg.imglib.function.RealTypeConverter;
import mpicbg.imglib.image.Image;
import mpicbg.imglib.image.ImageFactory;
import mpicbg.imglib.multithreading.Chunk;
import mpicbg.imglib.multithreading.SimpleMultiThreading;
import mpicbg.imglib.outofbounds.OutOfBoundsStrategyMirrorFactory;
import mpicbg.imglib.type.numeric.RealType;
import mpicbg.imglib.type.numeric.real.FloatType;
import mpicbg.imglib.util.Util;

public class GaussianGradient2D <T extends RealType<T>> extends MultiThreadedBenchmarkAlgorithm implements OutputAlgorithm<FloatType> {

	private Image<T> source;
	private double sigma;
	private Image<FloatType> Dx;
	private Image<FloatType> Dy;
	private List<Image<FloatType>> components = new ArrayList<Image<FloatType>>(2);


	/*
	 * CONSTRUCTOR
	 */


	public GaussianGradient2D(Image<T> source, double sigma) {
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

	@SuppressWarnings("unchecked")
	@Override
	public boolean process() {
		long start = System.currentTimeMillis();

		// Convert to float; needed to handle negative value properly
		final Image<FloatType> floatImage;
		if (source.createType().getClass().equals(FloatType.class)) {
			Object tmp = source;
			floatImage = (Image<FloatType>) tmp;
		} else {
			ImageConverter<T, FloatType> converter = new ImageConverter<T, FloatType>(
					source,
					new ImageFactory<FloatType>(new FloatType(), source.getContainerFactory()),
					new RealTypeConverter<T, FloatType>());
			converter.setNumThreads(numThreads);
			converter.checkInput();
			converter.process();
			floatImage = converter.getResult();
		}

		// In X
		GaussianConvolutionReal2D<FloatType> gx = new GaussianConvolutionReal2D<FloatType>(
				floatImage, 
				new OutOfBoundsStrategyMirrorFactory<FloatType>(), 
				new double[] { sigma, sigma} ) {

			protected void computeKernel() {

				double[][] kernel = getKernel();
				kernel[0] = Util.createGaussianKernel1DDouble( GaussianGradient2D.this.sigma, false );		
				kernel[1] = Util.createGaussianKernel1DDouble( GaussianGradient2D.this.sigma, true );
				int kSize = kernel[1].length;
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
		GaussianConvolutionReal2D<FloatType> gy = new GaussianConvolutionReal2D<FloatType>(
				floatImage, 
				new OutOfBoundsStrategyMirrorFactory<FloatType>(), 
				new double[] { sigma, sigma} ) {

			protected void computeKernel() {

				double[][] kernel = getKernel();
				kernel[0] = Util.createGaussianKernel1DDouble( GaussianGradient2D.this.sigma, true );		
				kernel[1] = Util.createGaussianKernel1DDouble( GaussianGradient2D.this.sigma, false );
				int kSize = kernel[0].length;
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

		long end = System.currentTimeMillis();
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

		Thread[] threads = new Thread[chunks.size()];
		for (int i = 0; i < threads.length; i++) {
			threads[i] = new Thread("Gradient norm thread "+i) {
				public void run() {

					Chunk chunk = chunks.get(ai.getAndIncrement());

					Cursor<FloatType> cx = Dx.createCursor();
					Cursor<FloatType> cy = Dy.createCursor();
					Cursor<FloatType> cn = norm.createCursor();

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
