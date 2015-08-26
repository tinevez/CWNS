package fiji.plugin.cwnt.segmentation;

import java.util.ArrayList;

import mpicbg.imglib.algorithm.MultiThreadedBenchmarkAlgorithm;
import mpicbg.imglib.algorithm.OutputAlgorithm;
import mpicbg.imglib.algorithm.histogram.Histogram;
import mpicbg.imglib.algorithm.histogram.HistogramBin;
import mpicbg.imglib.algorithm.histogram.HistogramKey;
import mpicbg.imglib.algorithm.histogram.discrete.DiscreteIntHistogramBinFactory;
import mpicbg.imglib.cursor.LocalizablePlaneCursor;
import mpicbg.imglib.image.Image;
import mpicbg.imglib.image.ImageFactory;
import mpicbg.imglib.type.logic.BitType;
import mpicbg.imglib.type.numeric.IntegerType;

public class OtsuThresholder2D <T extends IntegerType<T>> extends MultiThreadedBenchmarkAlgorithm implements OutputAlgorithm<BitType> {

	private Image<T> source;
	private Image<BitType> target;
	private double levelFactor;
	

	/*
	 * CONSTRUCTOR
	 */

	public OtsuThresholder2D(Image<T> source, double thresholdFactor) {
		super();
		this.source = source;
		this.levelFactor = thresholdFactor;
	}


	@Override
	public boolean checkInput() {
		return true;
	}

	@Override
	public boolean process() {
		
		// Create destination image
		target = new ImageFactory<BitType>(new BitType(), source.getContainerFactory())
			.createImage(source.getDimensions(), "Tresholded");
		LocalizablePlaneCursor<BitType> targetCursor = target.getContainer().createLocalizablePlaneCursor(target);
		
		// Create cursor over source image
		LocalizablePlaneCursor<T> cursor = source.getContainer().createLocalizablePlaneCursor(source);
		int nslices = source.getDimension(2);
		int[] pos = source.createPositionArray();

		boolean check;
		ArrayList<HistogramKey<T>> keys;
		HistogramBin<T> bin;
		long[] histarray;
		int threshold;
		
		for (int z = 0; z < nslices; z++) {
			
			if (nslices > 1) { // If we get a 2D image
				pos[2] = z;
			}
			
			// Build histogram in given plane
			cursor.reset(0, 1, pos);
			Histogram<T> histoalgo = new Histogram<T>(new DiscreteIntHistogramBinFactory<T>(), cursor);
			check = histoalgo.checkInput() && histoalgo.process();
			if (!check) {
				errorMessage = histoalgo.getErrorMessage();
				return false;
			}

			// Put result in an int array
			keys = histoalgo.getKeys();
			histarray = new long[(int) cursor.getType().getMaxValue() + 1];
			for(HistogramKey<T> key : keys) {
				bin = histoalgo.getBin(key);
				histarray[bin.getCenter().getInteger()] =  bin.getCount();
			}

			// Get Otsu threshold
			threshold = otsuThresholdIndex(histarray, source.getDimension(0) * source.getDimension(1));
			threshold = (int) (threshold * levelFactor) ;
			
			
			// Iterate over target image in the plane
			targetCursor.reset(0, 1, pos);
			cursor.reset(0, 1, pos);
			while(targetCursor.hasNext()) {
				targetCursor.fwd();
				cursor.fwd();
				targetCursor.getType().set( cursor.getType().getInteger() > threshold );
			}

		}
		cursor.close();
		targetCursor.close();

		return true;
	}

	@Override
	public Image<BitType> getResult() {
		return target;
	}


	/**
	 * Given a histogram array <code>hist</code>, built with an initial amount of <code>nPoints</code>
	 * data item, this method return the bin index that thresholds the histogram in 2 classes.
	 * The threshold is performed using the Otsu Threshold Method,
	 * {@link http://www.labbookpages.co.uk/software/imgProc/otsuThreshold.html}.
	 * @param hist  the histogram array
	 * @param nPoints  the number of data items this histogram was built on
	 * @return the bin index of the histogram that thresholds it
	 */
	public static final int otsuThresholdIndex(final long[] hist, final long nPoints)     {
		long total = nPoints;

		double sum = 0;
		for (int t = 0 ; t < hist.length ; t++)
			sum += t * hist[t];

		double sumB = 0;
		int wB = 0;
		long wF = 0;

		double varMax = 0;
		int threshold = 0;

		for (int t = 0 ; t < hist.length ; t++) {
			wB += hist[t];               // Weight Background
			if (wB == 0) continue;

			wF = total - wB;                 // Weight Foreground
			if (wF == 0) break;

			sumB += (float) (t * hist[t]);

			double mB = sumB / wB;            // Mean Background
			double mF = (sum - sumB) / wF;    // Mean Foreground

			// Calculate Between Class Variance
			double varBetween = (float)wB * (float)wF * (mB - mF) * (mB - mF);

			// Check if new maximum found
			if (varBetween > varMax) {
				varMax = varBetween;
				threshold = t;
			}
		}
		return threshold;
	}


}
