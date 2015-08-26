package fiji.plugin.cwnt;

import fiji.plugin.cwnt.segmentation.CrownWearingSegmenter;
import fiji.plugin.trackmate.TrackMate_;
import fiji.plugin.trackmate.features.spot.BlobDescriptiveStatistics;
import fiji.plugin.trackmate.features.spot.SpotFeatureAnalyzer;
import fiji.plugin.trackmate.segmentation.SpotSegmenter;
import fiji.plugin.trackmate.visualization.TrackMateModelView;
import ij.plugin.PlugIn;

import java.util.ArrayList;
import java.util.List;

/**
 * An entry for CWNT that uses {@link TrackMate_} as a GUI.
 * @author Jean-Yves Tinevez 2011
 *
 */
public class TrackMate_CWNT implements PlugIn {

	@Override
	public void run(String arg0) {

		TrackMate_ plugin = new TrackMate_() {

			@SuppressWarnings("rawtypes")
			@Override
			protected List<SpotSegmenter> createSegmenterList() {
				List<SpotSegmenter> list = super.createSegmenterList();
				list.add(0, new CrownWearingSegmenter());
				return list;
			}

			@Override
			protected List<SpotFeatureAnalyzer> createSpotFeatureAnalyzerList() {
				List<SpotFeatureAnalyzer> analyzers = new ArrayList<SpotFeatureAnalyzer>(0);
				return analyzers;
			}

			@Override
			protected List<TrackMateModelView> createTrackMateModelViewList() {
				List<TrackMateModelView> views = new ArrayList<TrackMateModelView>(1);
				views.add(CWNT_.createLocalSliceDisplayer());
				return views;
			}

		};

		plugin.run(arg0);

	}

}
