package mpicbg.imglib.algorithm.gauss;

import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.MultiThreadedBenchmarkAlgorithm;
import net.imglib2.algorithm.OutputAlgorithm;
import net.imglib2.algorithm.gauss3.Gauss3;
import net.imglib2.exception.IncompatibleTypeException;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.array.ArrayLocalizingCursor;
import net.imglib2.img.basictypeaccess.array.FloatArray;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.ExtendedRandomAccessibleInterval;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

public class GaussianFilter2D< T extends RealType< T >> extends MultiThreadedBenchmarkAlgorithm implements OutputAlgorithm< Img< FloatType > >
{
	private static final String BASE_ERROR_MSG = "[GaussianFilter2D] ";

	private final RandomAccessibleInterval< T > source;

	private final double[] sigmas;

	private ArrayImg< FloatType, FloatArray > target;

	public GaussianFilter2D( final RandomAccessibleInterval< T > source, final double[] sigmas )
	{
		this.source = source;
		this.sigmas = sigmas;
	}

	@Override
	public boolean checkInput()
	{
		if ( !( source.numDimensions() == 2 || source.numDimensions() == 3 ) )
		{
			errorMessage = BASE_ERROR_MSG + "Only operates on 2D or 3D images.";
			return false;
		}
		if ( sigmas.length != 2 )
		{
			errorMessage = BASE_ERROR_MSG + "The sigma array must have 2 elements.";
			return false;
		}
		return true;
	}

	@SuppressWarnings( "unchecked" )
	@Override
	public boolean process()
	{
		final long start = System.currentTimeMillis();

		// Copy to float
		final ArrayImgFactory< FloatType > factory = new ArrayImgFactory< FloatType >();
		target = ( ArrayImg< FloatType, FloatArray > ) factory.create( source, new FloatType() );
		final ArrayLocalizingCursor< FloatType > cursor = target.localizingCursor();
		final RandomAccess< T > ra = source.randomAccess( target );
		while ( cursor.hasNext() )
		{
			cursor.fwd();
			ra.setPosition( cursor );
			cursor.get().set( ra.get().getRealFloat() );
		}

		final int ndims = target.numDimensions();
		if ( ndims == 3 )
		{
			final long nz = target.dimension( 2 );
			for ( int z = 0; z < nz; z++ )
			{
				final IntervalView< FloatType > slice = Views.hyperSlice( target, 2, z );
				final boolean ok = processSlice( slice );
				if ( !ok ) { return false; }
			}
		}
		else
		{
			final boolean ok = processSlice( target );
			if ( !ok ) { return false; }
		}

		final long end = System.currentTimeMillis();
		processingTime = end - start;
		return true;
	}

	private boolean processSlice( final RandomAccessibleInterval< FloatType > src )
	{
		// Gaussian filter.
		final ExtendedRandomAccessibleInterval< FloatType, RandomAccessibleInterval< FloatType >> extended = Views.extendMirrorSingle( src );
		try
		{
			Gauss3.gauss( sigmas, extended, src, numThreads );
		}
		catch ( final IncompatibleTypeException e )
		{
			errorMessage = BASE_ERROR_MSG + "Incompatible types: " + e.getMessage();
			e.printStackTrace();
			return false;
		}
		return true;
	}

	@Override
	public ArrayImg< FloatType, FloatArray > getResult()
	{
		return target;
	}

}
