package fiji.plugin.cwnt.test;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import fiji.plugin.cwnt.CWNT_;
import fiji.plugin.cwnt.segmentation.CrownWearingSegmenter;
import fiji.plugin.trackmate.TrackMate_;
import fiji.plugin.trackmate.features.spot.BlobDescriptiveStatistics;
import fiji.plugin.trackmate.features.spot.SpotFeatureAnalyzer;
import fiji.plugin.trackmate.segmentation.SpotSegmenter;
import fiji.plugin.trackmate.visualization.TrackMateModelView;

public class Test_Integration {
	
	public static void main(String[] args) {
		
		TrackMate_ plugin = new TrackMate_() {
			
			@SuppressWarnings("rawtypes")
			@Override
			protected List<SpotSegmenter> createSegmenterList() {
				List<SpotSegmenter> list = super.createSegmenterList();
				list.add(new CrownWearingSegmenter());
				return list;
			}
			
			@Override
			protected List<SpotFeatureAnalyzer> createSpotFeatureAnalyzerList() {
				List<SpotFeatureAnalyzer> analyzers = new ArrayList<SpotFeatureAnalyzer>(1);
				analyzers.add(new BlobDescriptiveStatistics());
				return analyzers;
			}
			
			@Override
			protected List<TrackMateModelView> createTrackMateModelViewList() {
				List<TrackMateModelView> views = new ArrayList<TrackMateModelView>(1);
				views.add(CWNT_.createLocalSliceDisplayer());
				return views;
			}
			
		};

		
//		File testImage = new File("/Users/tinevez/Projects/BRajasekaran/Data/Meta-nov7mdb18ssplus-embryo2-2.tif");
		File testImage = new File("E:/Users/JeanYves/Documents/Projects/BRajaseka/Data/Meta-nov7mdb18ssplus-embryo2-2.tif");
//		File testImage = new File("E:/Users/JeanYves/Documents/Projects/BRajaseka/Data/Meta-nov7mdb18ssplus-embryo2-1.tif");
//		File testImage = new File("/Users/tinevez/Desktop/Data/10-01-21-1hour-bis.tif");

		ImageJ.main(args);
		ImagePlus imp = IJ.openImage(testImage.getAbsolutePath());
		imp.show();

		plugin.run("");
		
	}

}
