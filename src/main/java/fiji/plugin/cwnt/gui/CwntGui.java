package fiji.plugin.cwnt.gui;

import static fiji.plugin.cwnt.segmentation.CrownWearingSegmenter.INTRO_TEXT;

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import fiji.plugin.cwnt.segmentation.CWSettings;
import fiji.plugin.cwnt.segmentation.NucleiMasker;
import fiji.plugin.trackmate.TrackMateModel;
import fiji.plugin.trackmate.gui.ActionChooserPanel;
import fiji.plugin.trackmate.gui.DisplayerPanel;
import fiji.plugin.trackmate.gui.SegmenterConfigurationPanel;
import fiji.plugin.trackmate.segmentation.SegmenterSettings;
import fiji.plugin.trackmate.visualization.TrackMateModelView;

public class CwntGui extends SegmenterConfigurationPanel {

	private static final long serialVersionUID = 8311893056188053483L;

	/*
	 * EVENTS
	 */
	/** This event is fired whenever the value of σ for the gaussian filtering in step 1 is changed by this GUI. */
	public final ActionEvent STEP1_PARAMETER_CHANGED = new ActionEvent(this, 0, "GaussianFilteringParameterChanged");
	/** This event is fired whenever parameters defining the anisotropic diffusion step 2 is changed by this GUI. */
	public final ActionEvent STEP2_PARAMETER_CHANGED = new ActionEvent(this, 1, "AnisotropicDiffusionParameterChanged");
	/** This event is fired whenever the value of σ for the gaussian derivatives computation in step 3 is changed by this GUI. */
	public final ActionEvent STEP3_PARAMETER_CHANGED = new ActionEvent(this, 2, "DerivativesParameterChanged");
	/** This event is fired whenever parameters specifying how to combine the final mask in step 4 is changed by this GUI. */
	public final ActionEvent STEP4_PARAMETER_CHANGED = new ActionEvent(this, 3, "MaskingParameterChanged");
	/** This event is fired when the user presses the 'go' button in the 4th tab. */
	public final ActionEvent GO_BUTTON_PRESSED = new ActionEvent(this, 4, "GoButtonPressed");
	/** This event is fired when the user changes the current tab. */
	public final ActionEvent TAB_CHANGED = new ActionEvent(this, 5, "TabChanged");

	/*
	 * LOCAL LISTENERS
	 */

	private ArrayList<ActionListener> listeners = new ArrayList<ActionListener>();

	private ChangeListener step1ChangeListener = new ChangeListener() {
		public void stateChanged(ChangeEvent e) { fireEvent(STEP1_PARAMETER_CHANGED);	}
	};

	private ChangeListener step2ChangeListener = new ChangeListener() {
		public void stateChanged(ChangeEvent e) { fireEvent(STEP2_PARAMETER_CHANGED); }
	};

	private ChangeListener step3ChangeListener = new ChangeListener() {
		public void stateChanged(ChangeEvent e) { fireEvent(STEP3_PARAMETER_CHANGED); }
	};

	private ChangeListener step4ChangeListener = new ChangeListener() {
		public void stateChanged(ChangeEvent e) { fireEvent(STEP4_PARAMETER_CHANGED); }
	};

	private KeyListener step1KeyListener = new KeyAdapter() {
		public void keyReleased(KeyEvent ke) { fireEvent(STEP1_PARAMETER_CHANGED); 	}
	};

	private KeyListener step2KeyListener = new KeyAdapter() {
		public void keyReleased(KeyEvent ke) { fireEvent(STEP2_PARAMETER_CHANGED); 	}
	};

	private KeyListener step3KeyListener = new KeyAdapter() {
		public void keyReleased(KeyEvent ke) { fireEvent(STEP3_PARAMETER_CHANGED); 	}
	};

	private KeyListener step4KeyListener = new KeyAdapter() {
		public void keyReleased(KeyEvent ke) { fireEvent(STEP4_PARAMETER_CHANGED); 	}
	};



	/*
	 * FONTS
	 */

	private final static Font SMALL_LABEL_FONT = new Font("Arial", Font.PLAIN, 12);
	private final static Font MEDIUM_LABEL_FONT = new Font("Arial", Font.PLAIN, 16);
	private final static Font BIG_LABEL_FONT = new Font("Arial", Font.PLAIN, 20);
	private final static Font TEXT_FIELD_FONT = new Font("Arial", Font.PLAIN, 11);

	/*
	 * PARAMETERS
	 */

	private double[] oldParams;

	/*
	 *  GUI elements
	 */

	private JTabbedPane tabbedPane;

	private int scale = 10;
	private final DecimalFormat df2d = new DecimalFormat("0.####");
	private JTextField gaussFiltSigmaText;
	private DoubleJSlider gaussFiltSigmaSlider;
	private JTextField aniDiffNIterText;
	private DoubleJSlider aniDiffNIterSlider;
	private JTextField aniDiffKappaText;
	private DoubleJSlider aniDiffKappaSlider;
	private JTextField gaussGradSigmaText;
	private DoubleJSlider gaussGradSigmaSlider;
	private DoubleJSlider gammaSlider;
	private JTextField gammaText;
	private DoubleJSlider alphaSlider;
	private JTextField alphaText;
	private DoubleJSlider betaSlider;
	private JTextField betaText;
	private JTextField epsilonText;
	private DoubleJSlider epsilonSlider;
	private JTextField deltaText;
	private DoubleJSlider deltaSlider;
	private JLabel lblEstimatedTime;
	/** The go button that launches the whole segmentation. */
	public JButton btnGo;

	/**
	 * In the array, the parameters are ordered as follow:
	 * <ol start="0">
	 * 	<li> the σ for the gaussian filtering in step 1
	 *  <li> the number of iteration for anisotropic filtering in step 2
	 *  <li> κ, the gradient threshold for anisotropic filtering in step 2
	 * 	<li> the σ for the gaussian derivatives in step 3
	 *  <li> γ, the <i>tanh</i> shift in step 4
	 *  <li> α, the gradient prefactor in step 4
	 *  <li> β, the laplacian positive magnitude prefactor in step 4
	 *  <li> ε, the hessian negative magnitude prefactor in step 4
	 *  <li> δ, the derivative sum scale in step 4
	 * </ol>
	 */
	private double[] params = NucleiMasker.DEFAULT_MASKING_PARAMETERS;

	/** The index of the tab that tune the second set of parameters. */
	public int indexPanelParameters2 = 2;
	/** The index of the tab that tune the first set of parameters. */
	public int indexPanelParameters1 = 1;

	private JLabel labelTargetImage;

	/*
	 * CONSTRUCTOR
	 */

	public CwntGui() {
		initGUI();
	}

	/*
	 * PUBLIC METHODS
	 */

	public void addActionListener(ActionListener listener) {
		listeners.add(listener);
	}

	public boolean removeActionListener(ActionListener listener) {
		return listeners.remove(listener);
	}

	public List<ActionListener> getActionListeners() {
		return listeners;
	}

	public void setDurationEstimate(double t) {
		lblEstimatedTime.setText(String.format("Processing duration estimate: %.0f min.", t));
	}

	@Override
	public void setSegmenterSettings(TrackMateModel model) {
		CWSettings settings = (CWSettings) model.getSettings().segmenterSettings;
		setParameters(settings.getMaskingParameters());
		labelTargetImage.setText(model.getSettings().imp.getShortTitle());
	}

	@Override
	public SegmenterSettings getSegmenterSettings() {
		CWSettings settings = new CWSettings();
		settings.putMaskingParameters(params);
		return settings;
	}



	/**
	 * Return the parameters set by this GUI as a 9-elemts double array. In the array,
	 * the parameters are ordered as follow:
	 * <ol start="0">
	 * 	<li> the σ for the gaussian filtering in step 1
	 *  <li> the number of iteration for anisotropic filtering in step 2
	 *  <li> κ, the gradient threshold for anisotropic filtering in step 2
	 * 	<li> the σ for the gaussian derivatives in step 3
	 *  <li> γ, the <i>tanh</i> shift in step 4
	 *  <li> α, the gradient prefactor in step 4
	 *  <li> β, the laplacian positive magnitude prefactor in step 4
	 *  <li> ε, the hessian negative magnitude prefactor in step 4
	 *  <li> δ, the derivative sum scale in step 4
	 * </ol>
	 */
	public double[] getParameters() {
		return params;
	}

	private void setParameters(double[] params) {
		System.arraycopy(params, 0, this.params, 0, params.length);
	}

	public int getSelectedIndex() {
		return tabbedPane.getSelectedIndex();
	}

	/*
	 * PRIVATE METHODS
	 */

	private double[] collectParameters() throws NumberFormatException {
		double gaussFilterSigma = Double.parseDouble(gaussFiltSigmaText.getText());
		double nIterAnDiff = (int)  Double.parseDouble(aniDiffNIterText.getText());
		double kappa = Double.parseDouble(aniDiffKappaText.getText());
		double gaussGradSigma = Double.parseDouble(gaussGradSigmaText.getText());
		double gamma = Double.parseDouble(gammaText.getText());
		double alpha = Double.parseDouble(alphaText.getText());
		double beta = Double.parseDouble(betaText.getText());
		double epsilon = Double.parseDouble(epsilonText.getText());
		double delta = Double.parseDouble(deltaText.getText());
		return new double[] {
				gaussFilterSigma,
				nIterAnDiff,
				kappa,
				gaussGradSigma,
				gamma,
				alpha,
				beta,
				epsilon,
				delta
		};
	}

	private void fireEvent(final ActionEvent event) {
		if ( event == STEP1_PARAMETER_CHANGED ||
				event == STEP2_PARAMETER_CHANGED ||
				event == STEP3_PARAMETER_CHANGED ||
				event == STEP4_PARAMETER_CHANGED) {
			try {
				params = collectParameters();
			} catch (NumberFormatException nfe) {
				return;
			}
			if (Arrays.equals(params, oldParams)) {
				return; // We do not fire event if params did not change
			}

			oldParams = Arrays.copyOf(params, params.length);
		}
		for (ActionListener listener : listeners) {
			listener.actionPerformed(event);
		}
	}

	private void initGUI() {

		setLayout(new BorderLayout());

		tabbedPane = new JTabbedPane(JTabbedPane.TOP);
		tabbedPane.addChangeListener(new ChangeListener() {
			public void stateChanged(ChangeEvent e) { fireEvent(TAB_CHANGED);	}
		});
		add(tabbedPane);

		{
			JPanel panelIntroduction = new JPanel();
			tabbedPane.addTab("Intro", null, panelIntroduction, null);
			panelIntroduction.setLayout(null);

			JLabel lblCrownwearingNucleiTracker = new JLabel("Crown-Wearing Nuclei Tracker");
			lblCrownwearingNucleiTracker.setFont(BIG_LABEL_FONT);
			lblCrownwearingNucleiTracker.setHorizontalAlignment(SwingConstants.CENTER);
			lblCrownwearingNucleiTracker.setBounds(10, 11, 325, 30);
			panelIntroduction.add(lblCrownwearingNucleiTracker);

			JLabel lblTargetImage = new JLabel("Target image:");
			lblTargetImage.setFont(MEDIUM_LABEL_FONT);
			lblTargetImage.setBounds(10, 52, 325, 19);
			panelIntroduction.add(lblTargetImage);

			labelTargetImage = new JLabel();
			labelTargetImage.setHorizontalAlignment(SwingConstants.CENTER);
			labelTargetImage.setFont(MEDIUM_LABEL_FONT);
			labelTargetImage.setBounds(10, 82, 325, 19);
			panelIntroduction.add(labelTargetImage);

			JLabel labelIntro = new JLabel(INTRO_TEXT);
			labelIntro.setFont(SMALL_LABEL_FONT.deriveFont(11f));
			labelIntro.setBounds(10, 112, 325, 358);
			panelIntroduction.add(labelIntro);
		}

		JPanel panelParams1 = new JPanel();
		{
			tabbedPane.addTab("Param set 1", null, panelParams1, null);
			panelParams1.setLayout(null);

			JLabel lblNewLabel = new JLabel("Parameter set 1");
			lblNewLabel.setFont(BIG_LABEL_FONT);
			lblNewLabel.setHorizontalAlignment(SwingConstants.CENTER);
			lblNewLabel.setBounds(10, 11, 325, 29);
			panelParams1.add(lblNewLabel);

			JLabel lblFiltering = new JLabel("1. Filtering");
			lblFiltering.setFont(MEDIUM_LABEL_FONT);
			lblFiltering.setBounds(10, 64, 325, 29);
			panelParams1.add(lblFiltering);

			{
				JLabel lblGaussianFilter = new JLabel("Gaussian filter \u03C3:");
				lblGaussianFilter.setFont(SMALL_LABEL_FONT);
				lblGaussianFilter.setBounds(10, 104, 325, 14);
				panelParams1.add(lblGaussianFilter);

				gaussFiltSigmaSlider = new DoubleJSlider(0, 5*scale, (int) (params[0]*scale), scale);
				gaussFiltSigmaSlider.setBounds(10, 129, 255, 23);
				panelParams1.add(gaussFiltSigmaSlider);

				gaussFiltSigmaText = new JTextField(""+params[0]);
				gaussFiltSigmaText.setHorizontalAlignment(SwingConstants.CENTER);
				gaussFiltSigmaText.setBounds(275, 129, 60, 23);
				gaussFiltSigmaText.setFont(TEXT_FIELD_FONT);
				panelParams1.add(gaussFiltSigmaText);

				link(gaussFiltSigmaSlider, gaussFiltSigmaText);
				gaussFiltSigmaSlider.addChangeListener(step1ChangeListener);
				gaussFiltSigmaText.addKeyListener(step1KeyListener);
			}

			JLabel lblAnisotropicDiffusion = new JLabel("2. Anisotropic diffusion");
			lblAnisotropicDiffusion.setFont(MEDIUM_LABEL_FONT);
			lblAnisotropicDiffusion.setBounds(10, 181, 325, 29);
			panelParams1.add(lblAnisotropicDiffusion);

			{
				JLabel lblNumberOfIterations = new JLabel("Number of iterations:");
				lblNumberOfIterations.setFont(SMALL_LABEL_FONT);
				lblNumberOfIterations.setBounds(10, 221, 325, 14);
				panelParams1.add(lblNumberOfIterations);

				aniDiffNIterText = new JTextField();
				aniDiffNIterText.setHorizontalAlignment(SwingConstants.CENTER);
				aniDiffNIterText.setText(""+params[1]);
				aniDiffNIterText.setFont(TEXT_FIELD_FONT);
				aniDiffNIterText.setBounds(275, 246, 60, 23);
				panelParams1.add(aniDiffNIterText);

				aniDiffNIterSlider = new DoubleJSlider(1, 10, (int) params[1], 1);
				aniDiffNIterSlider.setBounds(10, 246, 255, 23);
				panelParams1.add(aniDiffNIterSlider);

				link(aniDiffNIterSlider, aniDiffNIterText);
				aniDiffNIterSlider.addChangeListener(step2ChangeListener);
				aniDiffNIterText.addKeyListener(step2KeyListener);

			}

			{
				JLabel lblGradientDiffusionThreshold = new JLabel("Gradient diffusion threshold \u03BA:");
				lblGradientDiffusionThreshold.setFont(SMALL_LABEL_FONT);
				lblGradientDiffusionThreshold.setBounds(10, 280, 325, 14);
				panelParams1.add(lblGradientDiffusionThreshold);

				aniDiffKappaText = new JTextField();
				aniDiffKappaText.setHorizontalAlignment(SwingConstants.CENTER);
				aniDiffKappaText.setText(""+params[2]);
				aniDiffKappaText.setFont(TEXT_FIELD_FONT);
				aniDiffKappaText.setBounds(275, 305, 60, 23);
				panelParams1.add(aniDiffKappaText);

				aniDiffKappaSlider = new DoubleJSlider(1, 100, (int) params[2], 1);
				aniDiffKappaSlider.setBounds(10, 305, 255, 23);
				panelParams1.add(aniDiffKappaSlider);

				link(aniDiffKappaSlider, aniDiffKappaText);
				aniDiffKappaSlider.addChangeListener(step2ChangeListener);
				aniDiffKappaText.addKeyListener(step2KeyListener);
			}

			JLabel lblDerivativesCalculation = new JLabel("3. Derivatives calculation");
			lblDerivativesCalculation.setFont(new Font("Arial", Font.PLAIN, 16));
			lblDerivativesCalculation.setBounds(10, 351, 325, 29);
			panelParams1.add(lblDerivativesCalculation);

			{
				JLabel lblGaussianGradient = new JLabel("Gaussian gradient \u03C3:");
				lblGaussianGradient.setFont(new Font("Arial", Font.PLAIN, 12));
				lblGaussianGradient.setBounds(10, 391, 325, 14);
				panelParams1.add(lblGaussianGradient);

				gaussGradSigmaText = new JTextField();
				gaussGradSigmaText.setHorizontalAlignment(SwingConstants.CENTER);
				gaussGradSigmaText.setText(""+params[3]);
				gaussGradSigmaText.setBounds(275, 416, 60, 23);
				panelParams1.add(gaussGradSigmaText);

				gaussGradSigmaSlider = new DoubleJSlider(0, 5*scale, (int) (params[3]*scale), scale);
				gaussGradSigmaSlider.setBounds(10, 416, 255, 23);
				panelParams1.add(gaussGradSigmaSlider);

				link(gaussGradSigmaSlider, gaussGradSigmaText);
				gaussGradSigmaSlider.addChangeListener(step3ChangeListener);
				gaussGradSigmaText.addKeyListener(step3KeyListener);
			}

		}

		JPanel panelParams2 = new JPanel();
		{
			tabbedPane.addTab("Param set 2", panelParams2);
			panelParams2.setLayout(null);

			JLabel lblParameterSet = new JLabel("Parameter set 2");
			lblParameterSet.setHorizontalAlignment(SwingConstants.CENTER);
			lblParameterSet.setFont(BIG_LABEL_FONT);
			lblParameterSet.setBounds(10, 11, 325, 29);
			panelParams2.add(lblParameterSet);

			JLabel lblMasking = new JLabel("4. Masking");
			lblMasking.setFont(MEDIUM_LABEL_FONT);
			lblMasking.setBounds(10, 51, 325, 29);
			panelParams2.add(lblMasking);


			{
				JLabel gammeLabel = new JLabel("\u03B3: tanh shift");
				gammeLabel.setFont(SMALL_LABEL_FONT);
				gammeLabel.setBounds(10, 106, 325, 14);
				panelParams2.add(gammeLabel);

				gammaSlider = new DoubleJSlider(-5*scale, 5*scale, (int) (params[4]*scale), scale);
				gammaSlider.setBounds(10, 131, 255, 23);
				panelParams2.add(gammaSlider);

				gammaText = new JTextField();
				gammaText.setText(""+params[4]);
				gammaText.setFont(TEXT_FIELD_FONT);
				gammaText.setBounds(275, 131, 60, 23);
				panelParams2.add(gammaText);

				link(gammaSlider, gammaText);
				gammaSlider.addChangeListener(step4ChangeListener);
				gammaSlider.addKeyListener(step4KeyListener);
			}
			{
				JLabel lblNewLabel_3 = new JLabel("\u03B1: gradient prefactor");
				lblNewLabel_3.setFont(SMALL_LABEL_FONT);
				lblNewLabel_3.setBounds(10, 165, 325, 14);
				panelParams2.add(lblNewLabel_3);

				alphaSlider = new DoubleJSlider(0, 20*scale, (int) (params[5]*scale), scale);
				alphaSlider.setBounds(10, 190, 255, 23);
				panelParams2.add(alphaSlider);

				alphaText = new JTextField(""+params[5]);
				alphaText.setFont(TEXT_FIELD_FONT);
				alphaText.setBounds(275, 190, 60, 23);
				panelParams2.add(alphaText);

				link(alphaSlider, alphaText);
				alphaSlider.addChangeListener(step4ChangeListener);
				alphaText.addKeyListener(step4KeyListener);
			}
			{
				JLabel betaLabel = new JLabel("\u03B2: positive laplacian magnitude prefactor");
				betaLabel.setFont(SMALL_LABEL_FONT);
				betaLabel.setBounds(10, 224, 325, 14);
				panelParams2.add(betaLabel);

				betaSlider = new DoubleJSlider(0, 20*scale, (int) (params[6]*scale), scale);
				betaSlider.setBounds(10, 249, 255, 23);
				panelParams2.add(betaSlider);

				betaText = new JTextField();
				betaText.setFont(TEXT_FIELD_FONT);
				betaText.setText(""+params[6]);
				betaText.setBounds(275, 249, 60, 23);
				panelParams2.add(betaText);

				link(betaSlider, betaText);
				betaSlider.addChangeListener(step4ChangeListener);
				betaText.addKeyListener(step4KeyListener);
			}
			{
				JLabel epsilonLabel = new JLabel("\u03B5: negative hessian magnitude");
				epsilonLabel.setFont(SMALL_LABEL_FONT);
				epsilonLabel.setBounds(10, 283, 325, 14);
				panelParams2.add(epsilonLabel);

				epsilonSlider = new DoubleJSlider(0, 20*scale, (int) (scale*params[7]), scale);
				epsilonSlider.setBounds(10, 308, 255, 23);
				panelParams2.add(epsilonSlider);

				epsilonText = new JTextField();
				epsilonText.setFont(TEXT_FIELD_FONT);
				epsilonText.setText(""+params[7]);
				epsilonText.setBounds(275, 308, 60, 23);
				panelParams2.add(epsilonText);

				link(epsilonSlider, epsilonText);
				epsilonSlider.addChangeListener(step4ChangeListener);
				epsilonText.addKeyListener(step4KeyListener);
			}
			{
				JLabel deltaLabel = new JLabel("\u03B4: derivatives sum scale");
				deltaLabel.setFont(SMALL_LABEL_FONT);
				deltaLabel.setBounds(20, 342, 325, 14);
				panelParams2.add(deltaLabel);

				deltaText = new JTextField();
				deltaText.setFont(TEXT_FIELD_FONT);
				deltaText.setText(""+params[8]);
				deltaText.setBounds(275, 367, 60, 23);
				panelParams2.add(deltaText);

				deltaSlider = new DoubleJSlider(0, 5*scale, (int) (params[8]*scale), scale);
				deltaSlider.setBounds(10, 367, 255, 23);
				panelParams2.add(deltaSlider);

				link(deltaSlider, deltaText);
				deltaSlider.addChangeListener(step4ChangeListener);
				deltaText.addKeyListener(step4KeyListener);
			}

			JLabel lblEquation = new JLabel("<html>M = \u00BD ( 1 + <i>tanh</i> ( \u03B3 - ( \u03B1 G + \u03B2 L + \u03B5 H ) / \u03B4 ) )</html>");
			lblEquation.setHorizontalAlignment(SwingConstants.CENTER);
			lblEquation.setFont(MEDIUM_LABEL_FONT);
			lblEquation.setBounds(10, 413, 325, 35);
			panelParams2.add(lblEquation);

			lblEstimatedTime = new JLabel("Tune parameters to get a duration estimate");
			lblEstimatedTime.setFont(SMALL_LABEL_FONT);
			lblEstimatedTime.setBounds(10, 71, 325, 23);
			panelParams2.add(lblEstimatedTime);
		}

//		{
//			JPanel panelRun = new JPanel();
//			tabbedPane.addTab("Run", null, panelRun, null);
//			panelRun.setLayout(null);
//
//			JLabel lblLaunchComputation = new JLabel("Launch computation");
//			lblLaunchComputation.setFont(BIG_LABEL_FONT);
//			lblLaunchComputation.setHorizontalAlignment(SwingConstants.CENTER);
//			lblLaunchComputation.setBounds(10, 11, 325, 31);
//			panelRun.add(lblLaunchComputation);
//
//			lblEstimatedTime = new JLabel("Tune parameters to get a duration estimate");
//			lblEstimatedTime.setFont(SMALL_LABEL_FONT);
//			lblEstimatedTime.setBounds(10, 71, 325, 23);
//			panelRun.add(lblEstimatedTime);
//
//			btnGo = new JButton("Go!");
//			btnGo.setFont(MEDIUM_LABEL_FONT);
//			btnGo.setIcon(new ImageIcon(CwntGui.class.getResource("resources/plugin_go.png")));
//			btnGo.setBounds(120, 135, 100, 50);
//			btnGo.addActionListener(new ActionListener() {
//				public void actionPerformed(ActionEvent e) { fireEvent(GO_BUTTON_PRESSED);	}
//			});
//			panelRun.add(btnGo);
//
//		}
	}
	
	public void setModelAndView(TrackMateModel model, TrackMateModelView view) {
		
		if (tabbedPane.getTabCount() > 4) {
			tabbedPane.removeTabAt(4);
		}
		DisplayerPanel displayerPanel = new DisplayerPanel(model);
		displayerPanel.jButtonShowTrackScheme.setVisible(false);
		displayerPanel.register(view);
		tabbedPane.addTab("Display settings", null, displayerPanel, null);

		if (tabbedPane.getTabCount() > 5) {
			tabbedPane.removeTabAt(5);
		}
		ActionChooserPanel actionPanel = new ActionChooserPanel(model, null, null);
		tabbedPane.addTab("Actions", null, actionPanel, null);
		
	}

	private void link(final DoubleJSlider slider, final JTextField text) {
		slider.addChangeListener(new ChangeListener(){
			@Override
			public void stateChanged(ChangeEvent e) {
				text.setText(df2d.format(slider.getScaledValue()));
			}
		});
		text.addKeyListener(new KeyAdapter(){
			@Override
			public void keyReleased(KeyEvent ke) {
				String typed = text.getText();
				if(!typed.matches("\\d+(\\.\\d+)?")) {
					return;
				}
				double value = Double.parseDouble(typed)*slider.scale;
				slider.setValue((int)value);
			}
		});
	}



}
