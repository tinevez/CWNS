package fiji.plugin.cwnt.segmentation;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import net.imglib2.Cursor;
import net.imglib2.RandomAccess;
import net.imglib2.RealCursor;
import net.imglib2.algorithm.MultiThreadedBenchmarkAlgorithm;
import net.imglib2.algorithm.OutputAlgorithm;
import net.imglib2.algorithm.stats.ComputeMinMax;
import net.imglib2.algorithm.stats.Histogram;
import net.imglib2.algorithm.stats.HistogramBinMapper;
import net.imglib2.algorithm.stats.RealBinMapper;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.multithreading.SimpleMultiThreading;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

@SuppressWarnings( "deprecation" )
public class OtsuThresholder2D< T extends RealType< T >> extends MultiThreadedBenchmarkAlgorithm implements OutputAlgorithm< Img< BitType >>
{

	private static final String BASE_ERROR_MESSAGE = "[OtsuThresholder2D] ";

	private final Img< T > source;

	private Img< BitType > target;

	private final double levelFactor;

	/*
	 * CONSTRUCTOR
	 */

	public OtsuThresholder2D( final Img< T > source, final double thresholdFactor )
	{
		super();
		this.source = source;
		this.levelFactor = thresholdFactor;
	}

	@Override
	public boolean checkInput()
	{
		return true;
	}

	@Override
	public boolean process()
	{

		// Create destination image
		target = new ArrayImgFactory< BitType >().create( source, new BitType() );
		final long nslices = source.dimension( 2 );

		final AtomicInteger aj = new AtomicInteger( 0 );
		final AtomicBoolean ok = new AtomicBoolean( true );

		final Thread[] threads = SimpleMultiThreading.newThreads( numThreads );

		for ( int i = 0; i < threads.length; i++ )
		{
			threads[ i ] = new Thread( BASE_ERROR_MESSAGE + "Otsu tresholder thread " + i )
			{

				@Override
				public void run()
				{

					for ( int z = aj.getAndIncrement(); z < nslices; z = aj.getAndIncrement() )
					{

						final IntervalView< T > slice = Views.hyperSlice( source, 2, z );

						// Find min & max inside plane.
						final T min = source.firstElement().createVariable();
						final T max = source.firstElement().createVariable();
						ComputeMinMax.computeMinMax( slice, min, max );

						// Compute histogram.
						final RealCursor< T > c = slice.cursor();
						final HistogramBinMapper< T > mapper = new RealBinMapper< T >( min, max, 500 );
						final Histogram< T > histo = new Histogram< T >( mapper, c );

						if ( !histo.checkInput() || !histo.process() )
						{
							errorMessage = BASE_ERROR_MESSAGE + histo.getErrorMessage();
							ok.set( false );
							return;
						}

						// Put result in an int array
						final int[] histogram = histo.getHistogram();

						// Get Otsu threshold
						final int thresholdIndex = otsuThresholdIndex( histogram, slice.size() );
						final T threshold = histo.getBinCenter( thresholdIndex );
						threshold.mul( levelFactor );

						// Iterate over target image in the plane
						final Cursor< T > cursor = slice.cursor();
						final RandomAccess< BitType > ra = Views.hyperSlice( target, 2, z ).randomAccess( slice );

						while ( cursor.hasNext() )
						{
							cursor.fwd();
							ra.setPosition( cursor );
							ra.get().set( cursor.get().compareTo( threshold ) > 0 );
						}
					}

				}
			};
		}

		SimpleMultiThreading.startAndJoin( threads );
		return ok.get();
	}

	@Override
	public Img< BitType > getResult()
	{
		return target;
	}

	/**
	 * Given a histogram array <code>hist</code>, built with an initial amount
	 * of <code>nPoints</code> data item, this method return the bin index that
	 * thresholds the histogram in 2 classes. The threshold is performed using
	 * the Otsu Threshold Method, {@link http
	 * ://www.labbookpages.co.uk/software/imgProc/otsuThreshold.html}.
	 * 
	 * @param hist
	 *            the histogram array
	 * @param nPoints
	 *            the number of data items this histogram was built on
	 * @return the bin index of the histogram that thresholds it
	 */
	public static final int otsuThresholdIndex( final int[] hist, final long nPoints )
	{
		final long total = nPoints;

		double sum = 0;
		for ( int t = 0; t < hist.length; t++ )
			sum += t * hist[ t ];

		double sumB = 0;
		int wB = 0;
		long wF = 0;

		double varMax = 0;
		int threshold = 0;

		for ( int t = 0; t < hist.length; t++ )
		{
			wB += hist[ t ]; // Weight Background
			if ( wB == 0 )
				continue;

			wF = total - wB; // Weight Foreground
			if ( wF == 0 )
				break;

			sumB += t * hist[ t ];

			final double mB = sumB / wB; // Mean Background
			final double mF = ( sum - sumB ) / wF; // Mean Foreground

			// Calculate Between Class Variance
			final double varBetween = ( float ) wB * ( float ) wF * ( mB - mF ) * ( mB - mF );

			// Check if new maximum found
			if ( varBetween > varMax )
			{
				varMax = varBetween;
				threshold = t;
			}
		}
		return threshold;
	}

}
