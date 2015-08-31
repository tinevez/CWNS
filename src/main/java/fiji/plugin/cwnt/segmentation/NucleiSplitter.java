package fiji.plugin.cwnt.segmentation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import net.imglib2.Cursor;
import net.imglib2.RandomAccess;
import net.imglib2.algorithm.MultiThreadedBenchmarkAlgorithm;
import net.imglib2.labeling.Labeling;
import net.imglib2.labeling.LabelingType;
import net.imglib2.multithreading.SimpleMultiThreading;
import net.imglib2.roi.IterableRegionOfInterest;

import org.apache.commons.math3.ml.clustering.CentroidCluster;
import org.apache.commons.math3.ml.clustering.KMeansPlusPlusClusterer;

import fiji.plugin.trackmate.Spot;

@SuppressWarnings( "deprecation" )
public class NucleiSplitter extends MultiThreadedBenchmarkAlgorithm
{

	private static final boolean DEBUG = false;

	private static final String BASE_ERROR_MESSAGE = "[NucleiSplitter] ";

	/** The labelled image contained the nuclei to split. */
	private final Labeling< Integer > source;

	/**
	 * Nuclei volume top threshold. Nuclei with a volume larger than this
	 * threshold will be discarded and not considered for splitting.
	 */
	private final long volumeThresholdUp = 1000; // Bhavna code

	/**
	 * Nuclei volume bottom threshold. Nuclei with a volume smaller than this
	 * threshold will be discarded and not considered for splitting.
	 */
	private final long volumeThresholdBottom = 0; // Bhavna code

	/**
	 * Volume selectivity: nuclei with a volume larger than mean + this factor
	 * times the standard deviation will be considered for splitting.
	 */
	private final double stdFactor = 0.5;

	private ArrayList< Integer > nucleiToSplit;

	private ArrayList< Integer > thrashedLabels;

	private final List< Spot > spots;

	private final double[] calibration;

	private final Iterator< Integer > labelGenerator;

	/*
	 * CONSTRUCTOR
	 */

	public NucleiSplitter( final Labeling< Integer > source, final double[] calibration, final Iterator< Integer > labelGenerator )
	{
		super();
		this.source = source;
		this.calibration = calibration;
		this.labelGenerator = labelGenerator;
		this.spots = Collections.synchronizedList( new ArrayList< Spot >( ( int ) 1.5 * source.getLabels().size() ) );
	}

	/*
	 * METHODS
	 */

	public List< Spot > getResult()
	{
		return spots;
	}

	@Override
	public boolean checkInput()
	{
		return true;
	}

	@Override
	public boolean process()
	{
		final long start = System.currentTimeMillis();

		final long volumeEstimate = getVolumeEstimate();

		final Thread[] threads = new Thread[ numThreads ];
		final AtomicInteger ai = new AtomicInteger();

		for ( int i = 0; i < threads.length; i++ )
		{
			threads[ i ] = new Thread( BASE_ERROR_MESSAGE + "thread " + i )
			{
				@Override
				public void run()
				{
					long volume;
					int targetNucleiNumber;
					Integer label;
					for ( int j = ai.getAndIncrement(); j < nucleiToSplit.size(); j = ai.getAndIncrement() )
					{
						label = nucleiToSplit.get( j );
						volume = source.getArea( label );
						targetNucleiNumber = ( int ) ( volume / volumeEstimate );
						if ( targetNucleiNumber > 1 )
						{
							split( label, targetNucleiNumber );
						}
					}
				}
			};
		}
		SimpleMultiThreading.startAndJoin( threads );

		final long end = System.currentTimeMillis();
		processingTime = end - start;
		return true;
	}

	/*
	 * PRIVATE METHODS
	 */

	/**
	 * Split the volume in the source image with the given label in the given
	 * number of nuclei. Splitting is made using K-means++ clustering using
	 * calibrated euclidean distance.
	 */
	private void split( final Integer label, final int n )
	{

		// Harvest pixel coordinates in a collection of calibrated clusterable
		// points
		final int volume = ( int ) source.getArea( label );
		final Collection< CalibratedEuclideanIntegerPoint > pixels = new ArrayList< CalibratedEuclideanIntegerPoint >( volume );

		final IterableRegionOfInterest roi = source.getIterableRegionOfInterest( label );
		final Cursor< LabelingType< Integer >> cursor = roi.getIterableIntervalOverROI( source ).cursor();
		while ( cursor.hasNext() )
		{
			cursor.fwd();
			final int[] position = new int[ source.numDimensions() ];
			cursor.localize( position );
			pixels.add( new CalibratedEuclideanIntegerPoint( position, calibration ) );
		}

		// Do K-means++ clustering
		final KMeansPlusPlusClusterer< CalibratedEuclideanIntegerPoint > clusterer = new KMeansPlusPlusClusterer< CalibratedEuclideanIntegerPoint >( n );
		final List< CentroidCluster< CalibratedEuclideanIntegerPoint >> clusters = clusterer.cluster( pixels );

		// Create spots from clusters
		final RandomAccess< LabelingType< Integer >> ra = source.randomAccess( source );
		for ( final CentroidCluster< CalibratedEuclideanIntegerPoint > cluster : clusters )
		{
			// Relabel new clusters.
			List< Integer > currentLabel = null;

			final double[] centroid = new double[ 3 ];
			for ( final CalibratedEuclideanIntegerPoint p : cluster.getPoints() )
			{
				ra.setPosition( p );
				if ( null == currentLabel )
				{
					currentLabel = ra.get().intern( labelGenerator.next() );
				}
				ra.get().setLabeling( currentLabel );

				for ( int i = 0; i < centroid.length; i++ )
				{
					centroid[ i ] += p.getPoint()[ i ];
				}
			}
			for ( int i = 0; i < centroid.length; i++ )
			{
				centroid[ i ] /= cluster.getPoints().size();
			}

			final double voxelVolume = calibration[ 0 ] * calibration[ 1 ] * calibration[ 2 ];
			final double nucleusVol = cluster.getPoints().size() * voxelVolume;
			final double radius = Math.pow( 3 * nucleusVol / ( 4 * Math.PI ), 0.33333 );
			final double quality = 1.0 / n;
			// split spot get a quality of 1 over the number of spots in the
			// initial cluster
			final Spot spot = new Spot( centroid[ 0 ], centroid[ 1 ], centroid[ 2 ], radius, quality );
			synchronized ( spots )
			{
				spots.add( spot );
			}
		}
	}

	/**
	 * Get an estimate of the actual single nuclei volume, to use in subsequent
	 * steps, when splitting touching nuclei.
	 * <p>
	 * Executing this methods also sets the following fields:
	 * <ul>
	 * <li> {@link #thrashedLabels} the list of labels that should be erased from
	 * the source labeling. It contains the label of nuclei that are too big or
	 * too small to be enve considered for splitting.
	 * <li> {@link #nucleiToSplit} the list of labels for the nuclei that should
	 * be considered for splitting. They are between acceptable bounds, but have
	 * a volume too large compared to the computed estimate to be made of a
	 * single nucleus.
	 * </ul>
	 * 
	 * @return the best volume estimate, as a long primitive
	 */
	private long getVolumeEstimate()
	{

		final ArrayList< Integer > labels = new ArrayList< Integer >( source.getLabels() );

		// Discard nuclei too big or too small;
		thrashedLabels = new ArrayList< Integer >( labels.size() / 10 );
		long volume;
		for ( final Integer label : labels )
		{
			volume = source.getArea( label );
			if ( volume >= volumeThresholdUp || volume <= volumeThresholdBottom )
			{
				thrashedLabels.add( label );
			}
		}
		if ( DEBUG )
		{
			System.out.println( BASE_ERROR_MESSAGE + "Removing " + thrashedLabels.size() + " bad nuclei out of " + labels.size() );
		}
		labels.removeAll( thrashedLabels );
		final int nNuclei = labels.size();

		// Compute mean and std of volume distribution
		long sum = 0;
		long sum_sqr = 0;
		long v;
		for ( final Integer label : labels )
		{
			v = source.getArea( label );
			sum += v;
			sum_sqr += v * v;
		}
		final long mean = sum / nNuclei;
		final long std = ( long ) Math.sqrt( ( sum_sqr - sum * mean ) / nNuclei );

		// Harvest suspicious nuclei
		nucleiToSplit = new ArrayList< Integer >( nNuclei / 5 );
		final long splitThreshold = ( long ) ( mean + stdFactor * std );
		for ( final Integer label : labels )
		{
			volume = source.getArea( label );
			if ( volume >= splitThreshold )
			{
				nucleiToSplit.add( label );
			}
		}

		// Build non-suspicious nuclei list
		final ArrayList< Integer > nonSuspiciousNuclei = new ArrayList< Integer >( labels );
		nonSuspiciousNuclei.removeAll( nucleiToSplit );
		if ( DEBUG )
		{
			System.out.println( BASE_ERROR_MESSAGE + "Found " + nucleiToSplit.size() + " nuclei to split out of " + labels.size() );
		}

		// Harvest non-suspicious nuclei as spots
		final double voxelVolume = calibration[ 0 ] * calibration[ 1 ] * calibration[ 2 ];
		for ( final Integer label : nonSuspiciousNuclei )
		{
			final double nucleusVol = source.getArea( label ) * voxelVolume;
			final double radius = Math.pow( 3 * nucleusVol / ( 4 * Math.PI ), 0.33333 );
			final double[] coordinates = getCentroid( label );
			final Spot spot = new Spot( coordinates[ 0 ], coordinates[ 1 ], coordinates[ 2 ], radius, 1.0 );
			// non-suspicious spots get a quality of 1
			spots.add( spot );
		}

		final long volumeEstimate = mean;

		if ( DEBUG )
		{
			System.out.println( BASE_ERROR_MESSAGE + "Single nucleus volume estimate: " + volumeEstimate + " voxels" );
		}

		return volumeEstimate;
	}

	private double[] getCentroid( final Integer label )
	{
		final double[] centroid = new double[ 3 ];
		final int[] position = new int[ source.numDimensions() ];

		final IterableRegionOfInterest roi = source.getIterableRegionOfInterest( label );
		final Cursor< LabelingType< Integer >> cursor = roi.getIterableIntervalOverROI( source ).cursor();

		int npixels = 0;
		while ( cursor.hasNext() )
		{
			cursor.fwd();
			cursor.localize( position );
			for ( int i = 0; i < position.length; i++ )
			{
				centroid[ i ] += position[ i ] * calibration[ i ];
			}
			npixels++;
		}
		for ( int i = 0; i < centroid.length; i++ )
		{
			centroid[ i ] /= npixels;
		}
		return centroid;
	}

}
