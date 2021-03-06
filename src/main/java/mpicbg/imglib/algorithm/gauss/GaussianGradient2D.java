package mpicbg.imglib.algorithm.gauss;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicInteger;

import net.imglib2.Cursor;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.MultiThreadedBenchmarkAlgorithm;
import net.imglib2.algorithm.OutputAlgorithm;
import net.imglib2.algorithm.gauss3.Gauss3;
import net.imglib2.algorithm.gradient.PartialDerivative;
import net.imglib2.exception.IncompatibleTypeException;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.basictypeaccess.array.FloatArray;
import net.imglib2.multithreading.Chunk;
import net.imglib2.multithreading.SimpleMultiThreading;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.ExtendedRandomAccessibleInterval;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

/**
 * Takes the 2D gaussian derivatives.
 * <p>
 * 3D images are treated as a series of 2D slices.
 * 
 * @author Jean-Yves Tinevez.
 *
 * @param <T>
 */
@SuppressWarnings( "deprecation" )
public class GaussianGradient2D< T extends RealType< T >> extends MultiThreadedBenchmarkAlgorithm implements OutputAlgorithm< Img< FloatType > >
{

	private static final String BASE_ERROR_MSG = "[GaussianGradient2D] ";

	private final Img< T > source;

	private final double sigma;

	private ArrayImg< FloatType, FloatArray > Dx;

	private ArrayImg< FloatType, FloatArray > Dy;

	private final List< ArrayImg< FloatType, FloatArray >> components = new ArrayList< ArrayImg< FloatType, FloatArray >>( 2 );

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
	public boolean checkInput()
	{
		if ( !( source.numDimensions() == 2 || source.numDimensions() == 3 ) )
		{
			errorMessage = BASE_ERROR_MSG + "Only operates on 2D or 3D images.";
			return false;
		}
		return true;
	}

	@SuppressWarnings( "unchecked" )
	@Override
	public boolean process()
	{
		final long start = System.currentTimeMillis();

		final ArrayImgFactory< FloatType > factory = new ArrayImgFactory< FloatType >();
		final ArrayImg< FloatType, FloatArray > floatImage = ( ArrayImg< FloatType, FloatArray > ) factory.create( source, new FloatType() );

		// Copy to float.
		final Cursor< FloatType > cursor = floatImage.cursor( source );
		final RandomAccess< T > ra = source.randomAccess( source );
		while ( cursor.hasNext() )
		{
			cursor.fwd();
			ra.setPosition( cursor );
			cursor.get().set( ra.get().getRealFloat() );
		}

		// Create result holders.
		Dx = ( ArrayImg< FloatType, FloatArray > ) factory.create( source, new FloatType() );
		Dy = ( ArrayImg< FloatType, FloatArray > ) factory.create( source, new FloatType() );

		final int ndims = floatImage.numDimensions();
		if ( ndims == 3 )
		{
			final long nz = floatImage.dimension( 2 );
			for ( int z = 0; z < nz; z++ )
			{
				final IntervalView< FloatType > slice = Views.hyperSlice( floatImage, 2, z );
				final IntervalView< FloatType > targetSliceX = Views.hyperSlice( Dx, 2, z );
				final IntervalView< FloatType > targetSliceY = Views.hyperSlice( Dy, 2, z );
				final boolean ok = processSlice( slice, targetSliceX, targetSliceY );
				if ( !ok ) { return false; }
			}
		}
		else
		{
			final boolean ok = processSlice( floatImage, Dx, Dy );
			if ( !ok ) { return false; }
		}

		components.clear();
		components.add( Dx );
		components.add( Dy );

		final long end = System.currentTimeMillis();
		processingTime = end - start;
		return true;
	}

	private boolean processSlice( final RandomAccessibleInterval< FloatType > src, final RandomAccessibleInterval< FloatType > dx, final RandomAccessibleInterval< FloatType > dy )
	{
		// Gaussian filter.
		final ExtendedRandomAccessibleInterval< FloatType, RandomAccessibleInterval< FloatType >> extended = Views.extendMirrorSingle( src );
		try
		{
			Gauss3.gauss( new double[] { sigma, sigma }, extended, src, numThreads );
		}
		catch ( final IncompatibleTypeException e )
		{
			errorMessage = BASE_ERROR_MSG + "Incompatible types: " + e.getMessage();
			e.printStackTrace();
			return false;
		}

		// Derivatives
		PartialDerivative.gradientCentralDifference( extended, dx, 0 );
		PartialDerivative.gradientCentralDifference( extended, dy, 1 );

		return true;
	}

	public List< ArrayImg< FloatType, FloatArray >> getGradientComponents()
	{
		return components;
	}

	/**
	 * Returns the gradient norm.
	 */
	@Override
	public ArrayImg< FloatType, FloatArray > getResult()
	{
		final ArrayImgFactory< FloatType > factory = new ArrayImgFactory< FloatType >();
		@SuppressWarnings( "unchecked" )
		final ArrayImg< FloatType, FloatArray > norm = ( ArrayImg< FloatType, FloatArray > ) factory.create( Dx, new FloatType() );

		final Vector< Chunk > chunks = SimpleMultiThreading.divideIntoChunks( norm.size(), numThreads );
		final AtomicInteger ai = new AtomicInteger();

		final Thread[] threads = new Thread[ chunks.size() ];
		for ( int i = 0; i < threads.length; i++ )
		{
			threads[ i ] = new Thread( "Gradient norm thread " + i )
			{
				@Override
				public void run()
				{

					final Chunk chunk = chunks.get( ai.getAndIncrement() );

					final Cursor< FloatType > cx = Dx.cursor();
					final Cursor< FloatType > cy = Dy.cursor();
					final Cursor< FloatType > cn = norm.cursor();

					double x, y;
					cn.jumpFwd( chunk.getStartPosition() );
					cx.jumpFwd( chunk.getStartPosition() );
					cy.jumpFwd( chunk.getStartPosition() );
					for ( long j = 0; j < chunk.getLoopSize(); j++ )
					{
						cn.fwd();
						cx.fwd();
						cy.fwd(); // Ok because we have identical containers
						x = cx.get().get();
						y = cy.get().get();
						cn.get().setReal( Math.sqrt( x * x + y * y ) );
					}
				}
			};
		}

		SimpleMultiThreading.startAndJoin( threads );
		return norm;
	}

}
