package fiji.plugin.cwnt.segmentation;

import ij.IJ;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import mpicbg.imglib.algorithm.MultiThreadedBenchmarkAlgorithm;
import mpicbg.imglib.algorithm.labeling.AllConnectedComponents;
import mpicbg.imglib.container.planar.PlanarContainerFactory;
import mpicbg.imglib.cursor.LocalizableByDimCursor;
import mpicbg.imglib.cursor.LocalizableCursor;
import mpicbg.imglib.image.Image;
import mpicbg.imglib.image.ImageFactory;
import mpicbg.imglib.labeling.Labeling;
import mpicbg.imglib.labeling.LabelingType;
import mpicbg.imglib.type.logic.BitType;
import mpicbg.imglib.type.numeric.IntegerType;
import fiji.plugin.trackmate.Spot;
import fiji.plugin.trackmate.segmentation.SegmenterSettings;
import fiji.plugin.trackmate.segmentation.SpotSegmenter;

public class CrownWearingSegmenter<T extends IntegerType<T>>  extends MultiThreadedBenchmarkAlgorithm implements SpotSegmenter<T> {
	
	private Image<T> masked;
	private Image<T> source;
	private Image<BitType> thresholded;
	private Labeling<Integer> labeling;
	private List<Spot> spots;
	private float[] calibration;
	private CWSettings settings;

	/*
	 * CONSTRUCTOR	
	 */

	public CrownWearingSegmenter() {
		super();
	}
	
	/*
	 * METHODS
	 */
	
	@Override
	public String getInfoText() {
		return INTRO_TEXT;
	}

	@Override
	public void setTarget(Image<T> image, float[] calibration, SegmenterSettings settings) {
		this.source = image;
		this.calibration = calibration;
		this.settings = (CWSettings) settings;
	}

	@Override
	public SegmenterSettings createDefaultSettings() {
		return new CWSettings();
	}

	@Override
	public SpotSegmenter<T> createNewSegmenter() {
		return new CrownWearingSegmenter<T>();
	}

	@Override
	public List<Spot> getResult() {
		return spots;
	}

	@Override
	public String toString() {
		return "Crown-Wearing Segmenter";
	}

	@Override
	public boolean checkInput() {
		return true;
	}

	@Override
	public boolean process() {
		long start = System.currentTimeMillis();
		boolean check;
		
		// Crown wearing mask
		NucleiMasker<T> masker = new NucleiMasker<T>(source);
		masker.setNumThreads(numThreads);
		masker.setParameters(settings.getMaskingParameters());
		check = masker.process();
		if (check) {
			masked = masker.getResult();
		} else {
			errorMessage = masker.getErrorMessage();
			return false;
		}
		
		// Thresholding
		OtsuThresholder2D<T> thresholder = new OtsuThresholder2D<T>(masked, settings.thresholdFactor);
		thresholder.setNumThreads(numThreads);
		check = thresholder.process();
		if (check) {
			thresholded = thresholder.getResult();
		} else {
			errorMessage = thresholder.getErrorMessage();
			return false;
		}
		
		// Labeling
		Iterator<Integer> labelGenerator = AllConnectedComponents.getIntegerNames(0);
		
		PlanarContainerFactory containerFactory = new PlanarContainerFactory();
		ImageFactory<LabelingType<Integer>> imageFactory = new ImageFactory<LabelingType<Integer>>(new LabelingType<Integer>(), containerFactory);
		labeling = new Labeling<Integer>(imageFactory, thresholded.getDimensions(), "Labels");
		labeling.setCalibration(source.getCalibration());
		
		// 6-connected structuring element
		int[][] structuringElement = new int[][] { {-1, 0, 0}, {1, 0, 0}, {0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1} };
		labelAllConnectedComponents(labeling , thresholded, labelGenerator, structuringElement);
		
		// Splitting and spot creation
		NucleiSplitter splitter = new NucleiSplitter(labeling, calibration);
		if (!(splitter.checkInput() && splitter.process())) {
			IJ.error("Problem with splitter: "+splitter.getErrorMessage());
			return false;
		}
		spots = splitter.getResult();
		processingTime = System.currentTimeMillis() - start;
		return true;
	}

	public Labeling<Integer> getLabeling() {
		return labeling;
	}
	
	
	/*
	 * STATIC METHODS
	 */
	

	
	/**
	 * Label all connected components in the given image using an arbitrary
	 * structuring element.
	 * @param <T> the type of the labels to apply
	 * @param labeling Assign labels to this labeling space 
	 * @param img a binary image where true indicates parts of components
	 * @param names supplies names for the different components as needed
	 * @param structuringElement an array of offsets to a pixel of the
	 * pixels which are considered connected. For instance, a 4-connected
	 * structuring element would be "new int [][] {{-1,0},{1,0},{0,-1},{0,1}}".
	 * @throws NoSuchElementException if there are not enough names
	 * @author Lee Klaminsky - I took it from its imglib package to fix a bug in
	 * imglib1.
	 */
	private static final <T extends Comparable<T>> void labelAllConnectedComponents(
			Labeling<T> labeling, 
			Image<BitType> img,	
			Iterator<T> names, 
			int [][] structuringElement) throws NoSuchElementException	{
		
		LocalizableCursor<BitType> c			 	= img.createLocalizableCursor();
		LocalizableByDimCursor<BitType> bc 			= img.createLocalizableByDimCursor();
		LocalizableByDimCursor<LabelingType<T>> destCursor = labeling.createLocalizableByDimCursor();
		
		int [] srcPosition = img.createPositionArray();
		int [] destPosition = img.createPositionArray();
		int [] dimensions = labeling.getDimensions();
		
		PositionStack toDoList = new PositionStack(img.getNumDimensions()); 
		
		for (BitType t:c) {
			
			if (t.get()) {
				
				c.getPosition(srcPosition);
				
				boolean outOfBounds = false;
				for (int i=0; i<dimensions.length; i++) {
					if (srcPosition[i] >= dimensions[i]) {
						outOfBounds = true;
						break;
					}
				}
				
				if (outOfBounds) continue;
				
				destCursor.setPosition(srcPosition);
				
				/*
				 * Assign a label if no label has yet been assigned.
				 */
				if (destCursor.getType().getLabeling().isEmpty()) {
					
					List<T> currentLabel = destCursor.getType().intern(names.next());
					destCursor.getType().setLabeling(currentLabel);
					toDoList.push(srcPosition);
					
					while ( !toDoList.isEmpty() ) {
						
						/*
						 * Find neighbors at the position
						 */
						toDoList.pop(srcPosition);
						
						for (int [] offset:structuringElement) {
							
							outOfBounds = false;
							for (int i=0; i<offset.length; i++) {
								destPosition[i] = srcPosition[i] + offset[i];
								if ((destPosition[i] < 0) || (destPosition[i] >= dimensions[i])){
									outOfBounds = true;
									break;
								}
							}
							if (outOfBounds) continue;
							
							bc.setPosition(destPosition);
							if (bc.getType().get()) { 
								destCursor.setPosition(destPosition);
								
								if (destCursor.getType().getLabeling().isEmpty()) {
									destCursor.getType().setLabeling(currentLabel);
									toDoList.push(destPosition);
								}
								
							}
						}
					}
				}
			}
		}
		
		c.close();
		bc.close();
		destCursor.close();
	}
	
	
	private static final class PositionStack {
		private final int dimensions;
		private int [] storage;
		private int position = 0;
		
		public PositionStack(int dimensions) {
			this.dimensions = dimensions;
			storage = new int [100 * dimensions];
		}
		
		public void push(int [] position) {
			
			int insertPoint = this.position * dimensions;
			
			if (storage.length <= (insertPoint+dimensions)) {
				int [] newStorage = new int [storage.length * 3 / 2];
				System.arraycopy(storage, 0, newStorage, 0, storage.length);
				storage = newStorage;
			
			}
			
			System.arraycopy(position, 0, storage, insertPoint, dimensions);
			this.position++;
		}
		
		public void pop(int [] position) {
			this.position--;
			System.arraycopy(storage, this.position * dimensions, position, 0, dimensions);
		}
		
		public boolean isEmpty() {
			return position == 0;
		}
	}


	public static final String INTRO_TEXT = "<html>" +
			"<div align=\"justify\">" +
			"This plugin allows the segmentation and tracking of bright blobs objects, " +
			"typically nuclei imaged in 3D over time. " +
			"<p> " +
			"It is specially designed to deal with the case the developing zebra-fish " +
			"embryogenesis, where nuclei are densily packed, which complicates their detection. " +
			"To do so, this plugin operates in 2 steps:" +
			"<p>" +
			" - The image is first pre-processed, by computing a special mask that stresses" +
			"the nuclei boundaries. A crown-like mak is computed from the 2D spatial derivatives " +
			"of the image, and a masked image where the nuclei are better separated is generated. " +
			"<br>" +
			" - Then the nuclei are thresholded from the background of the masked image, " +
			"labeled in 3D and tracked over time. " +
			"<p>" +
			"Because the crown-like mask needs 9 parameters to be specified, this plugin offers " +
			"to test the value of paramters in the 2nd and 3rd tab of this GUI. The resulting masked" +
			"image and intermediate images will be computed over a limited area of the source image, " +
			"specified by the ROI. " +
			"<p> " +
			"Once you are happy with the parameters, mode to the 4th tab to launch the computation " +
			"in batch." +
			"</div>" +
			"<div align=\"right\"> " +
			"<tt>" +
			"Bhavna Rajasekaran <br>" +
			"Jean-Yves Tinevez <br>" +
			"Andrew Oates lab - MPI-CBG, Dresden, 2011 " +
			"</tt>" +
			"</div>" +
			"</html>" +
			"";
	
}
