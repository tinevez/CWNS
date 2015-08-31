package fiji.plugin.cwnt.segmentation;

import java.util.Collection;

import net.imglib2.Localizable;

import org.apache.commons.math3.ml.clustering.Clusterable;
import org.apache.commons.math3.util.FastMath;

/**
 * An naive implementation of {@link Clusterable} for points with double
 * coordinates, so as to be able to take into account non-isotropic calibration.
 * <p>
 * It implements {@link Localizable}, so that it can returns the pixel
 * coordinates where it was created.
 * <p>
 * However the {@link #getPoint()} method returns the <b>calibrated</b>
 * position, in physical units.
 * 
 * @author Jean-Yves Tinevez
 */
public class CalibratedEuclideanIntegerPoint implements Clusterable, Localizable
{

	private final double[] point;

	private final double[] calibration;

	private final int[] loc;

	public CalibratedEuclideanIntegerPoint( final int[] loc, final double[] calibration )
	{
		this.loc = loc;
		this.point = new double[ loc.length ];
		for ( int d = 0; d < loc.length; d++ )
		{
			point[ d ] = loc[ d ] * calibration[ d ];
		}
		this.calibration = calibration;
	}

	public double distanceFrom(final CalibratedEuclideanIntegerPoint other) {
	      double sum = 0;
		final double[] po = other.getPoint();
	      for (int i = 0; i < po.length; i++) {
	          final double dp = (point[i] - po[i]) * calibration[i];
	          sum += dp * dp;
	      }
	      return FastMath.sqrt(sum);
	}

	public CalibratedEuclideanIntegerPoint centroidOf(final Collection<CalibratedEuclideanIntegerPoint> points) {
		final int[] centroid = new int[point.length];
		for (final CalibratedEuclideanIntegerPoint p : points) {
			for (int i = 0; i < centroid.length; i++) {
				centroid[i] += p.getPoint()[i];
			}
		}
		for (int i = 0; i < centroid.length; i++) {
			centroid[i] /= points.size();
		} // centroid un-scaled position does not depend on scale, we can ignore calibration[] for its computation
		return new CalibratedEuclideanIntegerPoint(centroid, calibration);
	}

	@Override
	public double[] getPoint()
	{
		return point;
	}

	@Override
	public void localize( final float[] position )
	{
		for ( int d = 0; d < loc.length; d++ )
		{
			position[ d ] = loc[ d ];
		}
	}

	@Override
	public void localize( final double[] position )
	{
		for ( int d = 0; d < loc.length; d++ )
		{
			position[ d ] = loc[ d ];
		}
	}

	@Override
	public float getFloatPosition( final int d )
	{
		return loc[ d ];
	}

	@Override
	public double getDoublePosition( final int d )
	{
		return loc[ d ];
	}

	@Override
	public int numDimensions()
	{
		return loc.length;
	}

	@Override
	public void localize( final int[] position )
	{
		for ( int d = 0; d < loc.length; d++ )
		{
			position[ d ] = loc[ d ];
		}
	}

	@Override
	public void localize( final long[] position )
	{
		for ( int d = 0; d < loc.length; d++ )
		{
			position[ d ] = loc[ d ];
		}
	}

	@Override
	public int getIntPosition( final int d )
	{
		return loc[ d ];
	}

	@Override
	public long getLongPosition( final int d )
	{
		return loc[ d ];
	}

}
