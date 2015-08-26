package mpicbg.imglib.algorithm.gauss;

import mpicbg.imglib.image.Image;
import mpicbg.imglib.outofbounds.OutOfBoundsStrategyFactory;
import mpicbg.imglib.type.numeric.RealType;
import mpicbg.imglib.util.Util;

public class GaussianConvolutionReal2D< T extends RealType<T> > extends GaussianConvolutionReal<T> {

	public GaussianConvolutionReal2D( final Image<T> image, final OutOfBoundsStrategyFactory<T> outOfBoundsFactory, final double[] sigma ) {
		super( image, outOfBoundsFactory, sigma );
		numDimensions = sigma.length;
	}
	
	@Override
	protected void computeKernel() {
		for ( int d = 0; d < sigma.length; ++d )
			this.kernel[ d ] = Util.createGaussianKernel1DDouble( sigma[ d ], true );		
	}
	
	
	protected double[][] getKernel() {
		return kernel;
	}
}
