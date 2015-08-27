package fiji.plugin.cwnt;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;

import java.io.File;

import fiji.plugin.trackmate.TrackMatePlugIn_;

public class TrackMate_CWNT extends TrackMatePlugIn_
{
	public static void main( final String[] args )
	{

//		File testImage = new File("E:/Users/JeanYves/Documents/Projects/BRajaseka/Data/Meta-nov7mdb18ssplus-embryo2-1.tif");
		final File testImage = new File( "/Users/tinevez/Projects/BRajasekaran/Data/Meta-nov7mdb18ssplus-embryo2-1.tif" );

		ImageJ.main( args );
		final ImagePlus imp = IJ.openImage( testImage.getAbsolutePath() );
		imp.show();

		final TrackMate_CWNT plugin = new TrackMate_CWNT();
		plugin.run( "" );
	}
}
