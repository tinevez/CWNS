package fiji.plugin.cwnt.gui;

import javax.swing.JSlider;

/**
 * Copied from 
 * 
 * http://stackoverflow.com/questions/1548606/java-link-jslider-and-jtextfield-for-float-value
 *
 */
public class DoubleJSlider extends JSlider {

	private static final long serialVersionUID = 1719710761678017866L;
	public final int scale;

    public DoubleJSlider(int min, int max, int value, int scale) {
        super(min, max, value);
        this.scale = scale;
    }

    public double getScaledValue() {
        return ((double)super.getValue()) / this.scale;
    }

}
