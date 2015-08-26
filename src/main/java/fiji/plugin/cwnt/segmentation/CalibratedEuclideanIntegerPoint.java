package fiji.plugin.cwnt.segmentation;

import java.util.Collection;

import org.apache.commons.math.stat.clustering.Clusterable;
import org.apache.commons.math.util.FastMath;

/**
 * An naive implementation of {@link Clusterable} for points with double coordinates,
 * so as to be able to take into account non-isotropic calibration.
 * @author Jean-Yves Tinevez
 */
public class CalibratedEuclideanIntegerPoint implements Clusterable<CalibratedEuclideanIntegerPoint>{

	private final int[] point;
	private final float[] calibration;

	public CalibratedEuclideanIntegerPoint(final int[] point, final float[] calibration) {
		this.point = point;
		this.calibration = calibration;
	}

	@Override
	public double distanceFrom(final CalibratedEuclideanIntegerPoint other) {
	      double sum = 0;
	      final int[] po = other.getPoint();
	      for (int i = 0; i < po.length; i++) {
	          final double dp = (point[i] - po[i]) * calibration[i];
	          sum += dp * dp;
	      }
	      return FastMath.sqrt(sum);
	}

	@Override
	public CalibratedEuclideanIntegerPoint centroidOf(Collection<CalibratedEuclideanIntegerPoint> points) {
		int[] centroid = new int[point.length];
		for (CalibratedEuclideanIntegerPoint p : points) {
			for (int i = 0; i < centroid.length; i++) {
				centroid[i] += p.getPoint()[i];
			}
		}
		for (int i = 0; i < centroid.length; i++) {
			centroid[i] /= points.size();
		} // centroid un-scaled position does not depend on scale, we can ignore calibration[] for its computation
		return new CalibratedEuclideanIntegerPoint(centroid, calibration);
	}

	public int[] getPoint() {
		return point;
	}

}
