package org.esa.beam.meris.icol.ui;

import com.bc.ceres.binding.ConversionException;
import com.bc.ceres.binding.Property;
import com.bc.ceres.binding.PropertyContainer;
import com.bc.ceres.binding.PropertySet;
import com.bc.ceres.binding.ValidationException;
import com.bc.ceres.swing.TableLayout;
import com.bc.ceres.swing.binding.Binding;
import com.bc.ceres.swing.binding.BindingContext;
import com.bc.ceres.swing.selection.AbstractSelectionChangeListener;
import com.bc.ceres.swing.selection.SelectionChangeEvent;
import com.bc.jexp.ParseException;
import com.bc.jexp.Term;
import org.apache.commons.lang.SystemUtils;
import org.esa.beam.dataio.envisat.EnvisatConstants;
import org.esa.beam.dataio.envisat.EnvisatProductReader;
import org.esa.beam.framework.dataio.ProductReader;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.dataop.barithm.BandArithmetic;
import org.esa.beam.framework.gpf.ui.SourceProductSelector;
import org.esa.beam.framework.gpf.ui.TargetProductSelector;
import org.esa.beam.framework.gpf.ui.TargetProductSelectorModel;
import org.esa.beam.framework.ui.AppContext;
import org.esa.beam.framework.ui.ModalDialog;
import org.esa.beam.framework.ui.product.ProductExpressionPane;
import org.esa.beam.meris.icol.AeArea;
import org.esa.beam.meris.icol.landsat.common.LandsatConstants;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.HashMap;
import java.util.Map;


class IcolForm extends JTabbedPane {

    private JCheckBox rhoToaRayleigh;
    private JCheckBox rhoToaAerosol;
    private JCheckBox aeRayleigh;
    private JCheckBox aeAerosol;
    private JCheckBox alphaAot;

    private JCheckBox useAdvancedLandWaterMaskCheckBox;
    private JCheckBox icolAerosolForWaterCheckBox;
    private JCheckBox icolAerosolCase2CheckBox;
    private JRadioButton icolCtp;
    private JRadioButton userCtp;
    private JFormattedTextField ctpValue;
    private JFormattedTextField aerosolReferenceWavelengthValue;
    private JFormattedTextField angstroemValue;
    private JFormattedTextField aotValue;
    private final TargetProductSelector targetProductSelector;
    private SourceProductSelector sourceProductSelector;
    private SourceProductSelector cloudProductSelector;
    private JRadioButton rhoToaProductTypeButton;
    private JRadioButton radianceProductTypeButton;
    private ButtonGroup productTypeGroup;
    private ButtonGroup ctpGroup;
    private JCheckBox openclConvolutionCheckBox;
    private JComboBox aeAreaComboBox;
    private ButtonGroup landsatResolutionGroup;
    private JRadioButton landsatResolution300Button;
    private JRadioButton landsatResolution1200Button;
    private JLabel userCtpLabel;
    private JFormattedTextField landsatOzoneContentValue;
    private JFormattedTextField landsatPSurfValue;
    private JFormattedTextField landsatTM60Value;
    private JTextField landsatOutputProductsDir;

    private ButtonGroup landsatOutputProductTypeGroup;
    private JRadioButton landsatOutputProductTypeFullAEButton;
    private JRadioButton landsatOutputProductTypeUpscaleAEButton;
    private JRadioButton landsatOutputProductTypeDownscaleButton;
    private JRadioButton landsatOutputProductTypeFlagsButton;

    private JCheckBox landsatCloudFlagApplyBrightnessFilterCheckBox;
    private JCheckBox landsatCloudFlagApplyNdsiFilterCheckBox;
    private JCheckBox landsatCloudFlagApplyTemperatureFilterCheckBox;
    private JCheckBox landsatCloudFlagApplyNdviFilterCheckBox;
    private JFormattedTextField cloudBrightnessThresholdValue;
    private JFormattedTextField cloudNdviThresholdValue;
    private JFormattedTextField cloudNdsiThresholdValue;
    private JFormattedTextField cloudTM6ThresholdValue;

    private JCheckBox landsatLandFlagApplyNdviFilterCheckBox;
    private JCheckBox landsatLandFlagApplyTemperatureFilterCheckBox;
    private JFormattedTextField landNdviThresholdValue;
    private JFormattedTextField landTM6ThresholdValue;
    private ButtonGroup landsatSeasonGroup;
    private JRadioButton landsatWinterButton;
    private JRadioButton landsatSummerButton;

    private final AppContext appContext;
    private final BindingContext bc;
    private final PropertyContainer icolContainer;

    IcolForm(AppContext appContext, IcolModel icolModel, TargetProductSelector targetProductSelector) {
        this.appContext = appContext;

        bc = new BindingContext(icolModel.getPropertyContainer());
        this.targetProductSelector = targetProductSelector;
        sourceProductSelector = new SourceProductSelector(appContext,
                                                          "Input-Product (MERIS L1b, Landsat5 TM or Landsat7 ETM+):");
        cloudProductSelector = new SourceProductSelector(appContext, "Cloud-Product:");
        initComponents();
        JComboBox sourceComboBox = sourceProductSelector.getProductNameComboBox();
        final PropertyContainer valueContainer = targetProductSelector.getModel().getValueContainer();
        sourceComboBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                updateProductTypeSettings();
                if (isEnvisatSourceProduct(IcolForm.this.sourceProductSelector.getSelectedProduct()) &&
                    radianceProductTypeButton.isSelected()) {
                    valueContainer.setValue("formatName", EnvisatConstants.ENVISAT_FORMAT_NAME);
                }
            }
        });
        PropertyChangeListener formatNameChangeListener = new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                updateProductFormatChange();
            }
        };
        valueContainer.addPropertyChangeListener("formatName", formatNameChangeListener);
        icolContainer = icolModel.getPropertyContainer();

//        icolContainer.setValue("landsatOutputProductsDir", targetProductSelector.getProductDirTextField().getText());

        bindComponents();
        updateUIStates();
    }

    public void prepareShow() {
        sourceProductSelector.initProducts();
        cloudProductSelector.initProducts();
        setRhoToaBandSelectionPanelEnabled(rhoToaProductTypeButton.isSelected());
        updateProductTypeSettings();
    }

    public void prepareHide() {
        sourceProductSelector.releaseProducts();
        cloudProductSelector.releaseProducts();
    }

    boolean isEnvisatSourceProduct(Product sourceProduct) {
        if (sourceProduct != null) {
            ProductReader productReader = sourceProduct.getProductReader();
            if ((productReader instanceof EnvisatProductReader)) {
                return true;
            }
        }
        return false;
    }

    boolean isEnvisatOutputFormatSelected() {
        return targetProductSelector.getModel().getFormatName().equals(EnvisatConstants.ENVISAT_FORMAT_NAME);
    }

    private void bindComponents() {

        bc.bind("exportRhoToaRayleigh", rhoToaRayleigh);
        bc.bind("exportRhoToaAerosol", rhoToaAerosol);
        bc.bind("exportAeRayleigh", aeRayleigh);
        bc.bind("exportAeAerosol", aeAerosol);
        bc.bind("exportAlphaAot", alphaAot);

        Map<AbstractButton, Object> ctpGroupValueSet = new HashMap<AbstractButton, Object>(4);
        ctpGroupValueSet.put(icolCtp, false);
        ctpGroupValueSet.put(userCtp, true);
        bc.bind("useUserCtp", ctpGroup, ctpGroupValueSet);
        bc.bind("userCtp", ctpValue);

        bc.bind("icolAerosolForWater", icolAerosolForWaterCheckBox);
        bc.bind("icolAerosolCase2", icolAerosolCase2CheckBox);
        bc.bind("userAerosolReferenceWavelength", aerosolReferenceWavelengthValue);
        bc.bind("userAlpha", angstroemValue);
        bc.bind("userAot", aotValue);

        bc.bind("aeArea", aeAreaComboBox);
        bc.bind("useAdvancedLandWaterMask", useAdvancedLandWaterMaskCheckBox);

        // currently we don't use this
//        bc.bind("openclConvolution", openclConvolutionCheckBox);

        bc.bind("productType", productTypeGroup);
        bc.bind("sourceProduct", sourceProductSelector.getProductNameComboBox());
        bc.bind("cloudMaskProduct", cloudProductSelector.getProductNameComboBox());

        bc.bind("landsatTargetResolution", landsatResolutionGroup);
        bc.bind("landsatOutputProductType", landsatOutputProductTypeGroup);
        bc.bind("landsatUserOzoneContent", landsatOzoneContentValue);
        bc.bind("landsatUserPSurf", landsatPSurfValue);
        bc.bind("landsatUserTm60", landsatTM60Value);
        bc.bind("landsatOutputProductsDir", landsatOutputProductsDir);

        bc.bind("landsatCloudFlagApplyBrightnessFilter", landsatCloudFlagApplyBrightnessFilterCheckBox);
        bc.bind("landsatCloudFlagApplyNdviFilter", landsatCloudFlagApplyNdviFilterCheckBox);
        bc.bind("landsatCloudFlagApplyNdsiFilter", landsatCloudFlagApplyNdsiFilterCheckBox);
        bc.bind("landsatCloudFlagApplyTemperatureFilter", landsatCloudFlagApplyTemperatureFilterCheckBox);
        bc.bind("cloudBrightnessThreshold", cloudBrightnessThresholdValue);
        bc.bind("cloudNdviThreshold", cloudNdviThresholdValue);
        bc.bind("cloudNdsiThreshold", cloudNdsiThresholdValue);
        bc.bind("cloudTM6Threshold", cloudTM6ThresholdValue);

        bc.bind("landsatLandFlagApplyNdviFilter", landsatLandFlagApplyNdviFilterCheckBox);
        bc.bind("landsatLandFlagApplyTemperatureFilter", landsatLandFlagApplyTemperatureFilterCheckBox);
        bc.bind("landNdviThreshold", landNdviThresholdValue);
        bc.bind("landTM6Threshold", landTM6ThresholdValue);
        bc.bind("landsatSeason", landsatSeasonGroup);
    }

    private void initComponents() {
        if (SystemUtils.IS_OS_MAC || SystemUtils.IS_OS_MAC_OSX) {
            setPreferredSize(new Dimension(600, 850));
        } else {
            setPreferredSize(new Dimension(600, 750));
        }

        TableLayout layoutIO = new TableLayout(1);
        layoutIO.setTableAnchor(TableLayout.Anchor.NORTHWEST);
        layoutIO.setTableFill(TableLayout.Fill.HORIZONTAL);
        layoutIO.setTableWeightX(1.0);
        layoutIO.setCellWeightY(2, 0, 1.0);
        layoutIO.setTablePadding(2, 2);

        TableLayout processingParam = new TableLayout(1);
        processingParam.setTableAnchor(TableLayout.Anchor.NORTHWEST);
        processingParam.setTableFill(TableLayout.Fill.HORIZONTAL);
        processingParam.setTableWeightX(1.0);
        processingParam.setCellWeightY(2, 0, 1.0);
        processingParam.setTablePadding(2, 2);

        TableLayout merisParam = new TableLayout(1);
        merisParam.setTableAnchor(TableLayout.Anchor.NORTHWEST);
        merisParam.setTableFill(TableLayout.Fill.HORIZONTAL);
        merisParam.setTableWeightX(1.0);
        merisParam.setCellWeightY(4, 0, 1.0);
        merisParam.setTablePadding(2, 2);

        TableLayout landsatParam = new TableLayout(1);
        landsatParam.setTableAnchor(TableLayout.Anchor.NORTHWEST);
        landsatParam.setTableFill(TableLayout.Fill.HORIZONTAL);
        landsatParam.setTableWeightX(1.0);
        landsatParam.setCellWeightY(3, 0, 1.0);
        landsatParam.setTablePadding(2, 2);

        JPanel ioTab = new JPanel(layoutIO);
        JPanel processingParamTab = new JPanel(processingParam);
        JPanel merisParamTab = new JPanel(merisParam);
        JPanel landsatParamTab = new JPanel(landsatParam);
        addTab("I/O Parameters", ioTab);
        addTab("General Settings", processingParamTab);
        addTab("MERIS", merisParamTab);
        addTab("LANDSAT TM", landsatParamTab);

        JPanel inputPanel = sourceProductSelector.createDefaultPanel();
        ioTab.add(inputPanel);
        ioTab.add(targetProductSelector.createDefaultPanel());
        ioTab.add(new JLabel(""));

        JPanel generalProcessingPanel = createGeneralProcessingPanel();
        processingParamTab.add(generalProcessingPanel);

        JPanel productTypePanel = createProductTypePanel();
        merisParamTab.add(productTypePanel);

        JPanel rhoToaPanel = createRhoToaBandSelectionPanel();
        merisParamTab.add(rhoToaPanel);

        JPanel merisProcessingPanel = createMerisProcessingPanel();
        merisParamTab.add(merisProcessingPanel);

        JPanel ctpPanel = createCTPPanel();
        merisParamTab.add(ctpPanel);

        JPanel cloudPanel = createMerisCloudPanel();
        merisParamTab.add(cloudPanel);

        JPanel aerosolPanel = createAerosolPanel();
        processingParamTab.add(aerosolPanel);

        JPanel advancedOptionsPanel = new JPanel();
        // leave this panel empty for the moment
//        advancedOptionsPanel = createAdvancedOptionsPanel();
        processingParamTab.add(advancedOptionsPanel);

        JPanel landsatProcessingPanel = createLandsatProcessingPanel();
        landsatParamTab.add(landsatProcessingPanel);

        JPanel landsatAtmosphericParametersPanel = createLandsatAtmosphericParametersPanel();
        landsatParamTab.add(landsatAtmosphericParametersPanel);

        JPanel landsatCloudFlagSettingPanel = createLandsatCloudFlagSettingPanel();
        landsatParamTab.add(landsatCloudFlagSettingPanel);

        JPanel landsatLandFlagSettingPanel = createLandsatLandFlagSettingPanel();
        landsatParamTab.add(landsatLandFlagSettingPanel);

        merisParamTab.add(new JLabel(""));
    }

    private JPanel createRhoToaBandSelectionPanel() {
        TableLayout layout = new TableLayout(1);
        layout.setTableAnchor(TableLayout.Anchor.WEST);
        layout.setTableFill(TableLayout.Fill.HORIZONTAL);
        layout.setTableWeightX(1.0);
        layout.setTablePadding(2, 2);

        rhoToaRayleigh = new JCheckBox("TOA reflectances corrected for AE rayleigh (rho_toa_AERC)");
        rhoToaAerosol = new JCheckBox("TOA reflectances corrected for AE rayleigh and AE aerosol (rho_toa_AEAC)");
        aeRayleigh = new JCheckBox("AE rayleigh correction term (rho_aeRay)");
        aeAerosol = new JCheckBox("AE aerosol correction term (rho_aeAer)");
        alphaAot = new JCheckBox("alpha + aot");

        JPanel panel = new JPanel(layout);
        panel.setBorder(BorderFactory.createTitledBorder("RhoToa Product"));

        panel.add(new JLabel("Bands included in the rhoToa product:"));
        panel.add(rhoToaRayleigh);
        panel.add(rhoToaAerosol);
        panel.add(aeRayleigh);
        panel.add(aeAerosol);
        panel.add(alphaAot);

        return panel;
    }

    private JPanel createCTPPanel() {
        TableLayout layout = new TableLayout(3);
        layout.setTableAnchor(TableLayout.Anchor.WEST);
        layout.setTableFill(TableLayout.Fill.HORIZONTAL);
        layout.setColumnWeightX(0, 0.1);
        layout.setColumnWeightX(1, 0.1);
        layout.setColumnWeightX(2, 1.0);
        layout.setCellColspan(0, 0, 3);
        layout.setCellColspan(1, 0, 3);
        layout.setTablePadding(2, 2);
        layout.setCellPadding(2, 0, new Insets(0, 24, 0, 0));
        layout.setCellPadding(3, 0, new Insets(0, 24, 0, 0));
        JPanel panel = new JPanel(layout);

        panel.setBorder(BorderFactory.createTitledBorder("Cloud Top Pressure"));
        ctpGroup = new ButtonGroup();
        icolCtp = new JRadioButton("Compute by algorithm");
        icolCtp.setSelected(true);
        panel.add(icolCtp);
        ctpGroup.add(icolCtp);

        userCtp = new JRadioButton("Use constant value");
        userCtp.setSelected(false);

        panel.add(userCtp);
        ctpGroup.add(userCtp);

        ctpValue = new JFormattedTextField("1013.0");

        userCtpLabel = new JLabel("CTP: ");
        panel.add(userCtpLabel);
        panel.add(ctpValue);
        panel.add(new JPanel());

        ActionListener ctpActionListener = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                updateCtpUIstate();
            }
        };
        icolCtp.addActionListener(ctpActionListener);
        userCtp.addActionListener(ctpActionListener);

        return panel;
    }

    private JPanel createMerisCloudPanel() {
        JPanel panel = cloudProductSelector.createDefaultPanel();
        panel.setBorder(BorderFactory.createTitledBorder("Cloud Mask"));

        final JTextField textField = new JTextField(30);
        final Binding binding = bc.bind("cloudMaskExpression", textField);
        final JPanel subPanel = new JPanel(new BorderLayout(2, 2));
        subPanel.add(new JLabel("Mask expression:"), BorderLayout.NORTH);
        subPanel.add(textField, BorderLayout.CENTER);
        final JButton etcButton = new JButton("...");
        etcButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                ProductExpressionPane expressionPane;
                Product currentProduct = cloudProductSelector.getSelectedProduct();
                expressionPane = ProductExpressionPane.createBooleanExpressionPane(new Product[]{currentProduct},
                                                                                   currentProduct,
                                                                                   appContext.getPreferences());
                expressionPane.setCode((String) binding.getPropertyValue());
                if (expressionPane.showModalDialog(null, "Expression Editor") == ModalDialog.ID_OK) {
                    binding.setPropertyValue(expressionPane.getCode());
                }
            }
        });
        cloudProductSelector.addSelectionChangeListener(new AbstractSelectionChangeListener() {

            @Override
            public void selectionChanged(SelectionChangeEvent event) {
                updateMerisCloudMaskExpressionEditor(textField, etcButton);
            }
        });
        updateMerisCloudMaskExpressionEditor(textField, etcButton);
        subPanel.add(etcButton, BorderLayout.EAST);
        panel.add(subPanel);
        return panel;
    }

    private void updateMerisCloudMaskExpressionEditor(JTextField textField, JComponent etcButton) {
        Product selectedProduct = cloudProductSelector.getSelectedProduct();
        boolean hasProduct = selectedProduct != null;
        etcButton.setEnabled(hasProduct);
        textField.setEnabled(hasProduct);
        if (hasProduct) {
            Term term = null;
            try {
                term = BandArithmetic.parseExpression(textField.getText(), new Product[]{selectedProduct}, 0);
            } catch (ParseException ignore) {
            }
            if (term == null) {
                textField.setText("");
            }
        }
    }

    private void updateUIStates() {
        updateCtpUIstate();
        // currently we don't use this
//        openclConvolutionCheckBox.setEnabled(false);
    }

    private void updateCtpUIstate() {
        boolean userCtpSelected = userCtp.isSelected();
        userCtpLabel.setEnabled(userCtpSelected);
        ctpValue.setEnabled(userCtpSelected);
    }

    private JPanel createAerosolPanel() {
        TableLayout layout = new TableLayout(3);
        layout.setTableAnchor(TableLayout.Anchor.WEST);
        layout.setTableFill(TableLayout.Fill.HORIZONTAL);
        layout.setColumnWeightX(0, 0.1);
        layout.setColumnWeightX(1, 0.1);
        layout.setColumnWeightX(2, 1.0);
        layout.setTablePadding(2, 2);
        layout.setCellPadding(0, 0, new Insets(0, 24, 0, 0));
        layout.setCellPadding(1, 0, new Insets(0, 24, 0, 0));
        layout.setCellPadding(2, 0, new Insets(0, 24, 0, 0));
        layout.setCellColspan(3, 0, 3);
        JPanel panel = new JPanel(layout);

        panel.setBorder(BorderFactory.createTitledBorder("Aerosol Type Determination"));

        aerosolReferenceWavelengthValue = new JFormattedTextField();
        angstroemValue = new JFormattedTextField();
        aotValue = new JFormattedTextField();

        panel.add(new JLabel("Reference wavelength (nm): "));
        panel.add(aerosolReferenceWavelengthValue);
        panel.add(new JPanel());

        panel.add(new JLabel("Angstroem: "));
        panel.add(angstroemValue);
        panel.add(new JPanel());

        panel.add(new JLabel("AOT: "));
        panel.add(aotValue);
        panel.add(new JLabel(""));

//        icolAerosolForWaterCheckBox = new JCheckBox("Over water, compute aerosol type by AE algorithm");
//        icolAerosolForWaterCheckBox.setSelected(true);
//        panel.add(icolAerosolForWaterCheckBox);

        return panel;
    }

    private JPanel createGeneralProcessingPanel() {
        // table layout with a third 'empty' column
        TableLayout layout = new TableLayout(2);
        layout.setTableAnchor(TableLayout.Anchor.WEST);
        layout.setTableFill(TableLayout.Fill.HORIZONTAL);
        layout.setColumnWeightX(0, 1.0);
        layout.setColumnWeightX(1, 0.1);
        layout.setTablePadding(2, 2);
        layout.setCellColspan(0, 0, 2);
        layout.setCellColspan(1, 0, 2);
        JPanel panel = new JPanel(layout);

        panel.setBorder(BorderFactory.createTitledBorder("Processing"));

        aeAreaComboBox = new JComboBox();
        aeAreaComboBox.setRenderer(new AeAreaRenderer());
        panel.add(new JLabel("Where to apply the AE algorithm:"));
        panel.add(new JLabel());
        panel.add(aeAreaComboBox);
        panel.add(new JLabel());

//        useAdvancedLandWaterMaskCheckBox = new JCheckBox(
//                "Use advanced land/water mask.");
//        useAdvancedLandWaterMaskCheckBox.setSelected(true);
//        panel.add(useAdvancedLandWaterMaskCheckBox);

        return panel;
    }

    private JPanel createMerisProcessingPanel() {
        // table layout with a third 'empty' column
        TableLayout layout = new TableLayout(2);
        layout.setTableAnchor(TableLayout.Anchor.WEST);
        layout.setTableFill(TableLayout.Fill.HORIZONTAL);
        layout.setColumnWeightX(0, 1.0);
        layout.setColumnWeightX(1, 0.1);
        layout.setTablePadding(2, 2);
        layout.setCellColspan(0, 0, 2);
        layout.setCellColspan(1, 0, 2);
        layout.setCellColspan(2, 0, 2);
        layout.setCellColspan(3, 0, 2);
        layout.setCellColspan(4, 0, 2);
        JPanel panel = new JPanel(layout);

        panel.setBorder(BorderFactory.createTitledBorder("Processing"));

        icolAerosolForWaterCheckBox = new JCheckBox("Over water, compute aerosol type by AE algorithm");
        icolAerosolForWaterCheckBox.setSelected(true);
        panel.add(icolAerosolForWaterCheckBox);

        icolAerosolCase2CheckBox = new JCheckBox("Consider case 2 waters in AE algorithm");
        icolAerosolCase2CheckBox.setSelected(false);
        panel.add(icolAerosolCase2CheckBox);

        useAdvancedLandWaterMaskCheckBox = new JCheckBox(
                "Use advanced land/water mask.");
        useAdvancedLandWaterMaskCheckBox.setSelected(true);
        panel.add(useAdvancedLandWaterMaskCheckBox);

        return panel;
    }

    private JPanel createAdvancedOptionsPanel() {
        // table layout with a third 'empty' column
        TableLayout layout = new TableLayout(2);
        layout.setTableAnchor(TableLayout.Anchor.WEST);
        layout.setTableFill(TableLayout.Fill.HORIZONTAL);
        layout.setColumnWeightX(0, 1.0);
        layout.setColumnWeightX(1, 0.1);
        layout.setTablePadding(2, 2);
        layout.setCellColspan(0, 0, 2);
        layout.setCellColspan(1, 0, 2);
        JPanel panel = new JPanel(layout);

        panel.setBorder(BorderFactory.createTitledBorder("Advanced Options"));

        // currently we don't use this
//        openclConvolutionCheckBox = new JCheckBox(
//                "Perform convolutions with OpenCL (for unique aerosol type only, GPU hardware required)");
//        openclConvolutionCheckBox.setSelected(false);
//        openclConvolutionCheckBox.setEnabled(false);
//        panel.add(openclConvolutionCheckBox);

        return panel;
    }


    private JPanel createLandsatAtmosphericParametersPanel() {
        TableLayout layout = new TableLayout(3);
        layout.setTableAnchor(TableLayout.Anchor.WEST);
        layout.setTableFill(TableLayout.Fill.HORIZONTAL);
        layout.setColumnWeightX(0, 0.1);
        layout.setColumnWeightX(1, 0.1);
        layout.setColumnWeightX(2, 1.0);
        layout.setTablePadding(2, 2);
        layout.setCellPadding(0, 0, new Insets(0, 24, 0, 0));
        layout.setCellPadding(1, 0, new Insets(0, 24, 0, 0));
        layout.setCellPadding(2, 0, new Insets(0, 24, 0, 0));
        layout.setCellPadding(3, 0, new Insets(0, 24, 0, 0));

        JPanel panel = new JPanel(layout);

        panel.setBorder(BorderFactory.createTitledBorder("Atmospheric Parameters"));

        panel.add(new JLabel("Ozone content (cm atm): "));
        landsatOzoneContentValue = new JFormattedTextField(Double.toString(LandsatConstants.DEFAULT_OZONE_CONTENT));
        panel.add(landsatOzoneContentValue);
        panel.add(new JLabel(""));

        panel.add(new JLabel("Surface pressure (hPa): "));
        landsatPSurfValue = new JFormattedTextField(Double.toString(LandsatConstants.DEFAULT_SURFACE_PRESSURE));
        panel.add(landsatPSurfValue);
        panel.add(new JLabel(""));

        panel.add(new JLabel("Surface TM apparent temperature (K): "));
        landsatTM60Value = new JFormattedTextField(
                Double.toString(LandsatConstants.DEFAULT_SURFACE_TM_APPARENT_TEMPERATURE));
        panel.add(landsatTM60Value);
        panel.add(new JLabel(""));

        panel.add(new JPanel());

        return panel;
    }

    private JPanel createLandsatCloudFlagSettingPanel() {
        TableLayout layout = new TableLayout(3);
        layout.setTableAnchor(TableLayout.Anchor.WEST);
        layout.setTableFill(TableLayout.Fill.HORIZONTAL);
        layout.setColumnWeightX(0, 0.1);
        layout.setColumnWeightX(1, 0.1);
        layout.setColumnWeightX(2, 1.0);
        layout.setTablePadding(2, 2);
        layout.setCellPadding(0, 0, new Insets(0, 24, 0, 0));
        layout.setCellPadding(1, 0, new Insets(0, 48, 0, 0));
        layout.setCellPadding(2, 0, new Insets(0, 24, 0, 0));
        layout.setCellPadding(3, 0, new Insets(0, 48, 0, 0));
        layout.setCellPadding(4, 0, new Insets(0, 24, 0, 0));
        layout.setCellPadding(5, 0, new Insets(0, 48, 0, 0));
        layout.setCellPadding(6, 0, new Insets(0, 24, 0, 0));
        layout.setCellPadding(7, 0, new Insets(0, 48, 0, 0));
        JPanel panel = new JPanel(layout);

        panel.setBorder(BorderFactory.createTitledBorder("Cloud Flag Settings"));

        landsatCloudFlagApplyBrightnessFilterCheckBox =
                new JCheckBox("Brightness flag (set if TM3 > BT)");
        landsatCloudFlagApplyBrightnessFilterCheckBox.setSelected(true);
        panel.add(landsatCloudFlagApplyBrightnessFilterCheckBox);
        panel.add(new JLabel());
        panel.add(new JLabel());

        cloudBrightnessThresholdValue = new JFormattedTextField(
                Double.toString(LandsatConstants.DEFAULT_BRIGHTNESS_THRESHOLD));
        panel.add(new JLabel("Brightness threshold BT: "));
        panel.add(cloudBrightnessThresholdValue);
        panel.add(new JLabel());

        landsatCloudFlagApplyNdviFilterCheckBox =
                new JCheckBox("NDVI flag (set if NDVI < NDVIT, with NDVI = (TM4 - TM3)/(TM4 + TM3))");
        landsatCloudFlagApplyNdviFilterCheckBox.setSelected(true);
        panel.add(landsatCloudFlagApplyNdviFilterCheckBox);
        panel.add(new JLabel());
        panel.add(new JLabel());

        cloudNdviThresholdValue = new JFormattedTextField(Double.toString(LandsatConstants.DEFAULT_NDVI_CLOUD_THRESHOLD));
        panel.add(new JLabel("NDVI threshold NDVIT: "));
        panel.add(cloudNdviThresholdValue);
        panel.add(new JLabel());

        landsatCloudFlagApplyNdsiFilterCheckBox =
                new JCheckBox("NDSI flag (set if NDSI < NDSIT, with NDSI = (TM2 - TM5)/(TM2 + TM5))");
        landsatCloudFlagApplyNdsiFilterCheckBox.setSelected(true);
        panel.add(landsatCloudFlagApplyNdsiFilterCheckBox);
        panel.add(new JLabel());
        panel.add(new JLabel());

        cloudNdsiThresholdValue = new JFormattedTextField(Double.toString(LandsatConstants.DEFAULT_NDSI_THRESHOLD));
        panel.add(new JLabel("NDSI threshold NDSIT: "));
        panel.add(cloudNdsiThresholdValue);
        panel.add(new JLabel());

        landsatCloudFlagApplyTemperatureFilterCheckBox =
                new JCheckBox("Temperature flag (set if TM6 < TM6T)");
        landsatCloudFlagApplyTemperatureFilterCheckBox.setSelected(true);
        panel.add(landsatCloudFlagApplyTemperatureFilterCheckBox);
        panel.add(new JLabel());
        panel.add(new JLabel());

        cloudTM6ThresholdValue = new JFormattedTextField(Double.toString(LandsatConstants.DEFAULT_TM6_CLOUD_THRESHOLD));
        panel.add(new JLabel("Temperature threshold TM6T (K): "));
        panel.add(cloudTM6ThresholdValue);
        panel.add(new JLabel());

        return panel;
    }

    private JPanel createLandsatLandFlagSettingPanel() {
        TableLayout layout = new TableLayout(3);
        layout.setTableAnchor(TableLayout.Anchor.WEST);
        layout.setTableFill(TableLayout.Fill.HORIZONTAL);
        layout.setColumnWeightX(0, 0.1);
        layout.setColumnWeightX(1, 0.1);
        layout.setColumnWeightX(2, 1.0);
        layout.setTablePadding(2, 2);
        layout.setCellPadding(0, 0, new Insets(0, 24, 0, 0));
        layout.setCellPadding(1, 0, new Insets(0, 48, 0, 0));
        layout.setCellPadding(2, 0, new Insets(0, 24, 0, 0));
        layout.setCellPadding(3, 0, new Insets(0, 48, 0, 0));
        layout.setCellPadding(4, 0, new Insets(0, 48, 0, 0));
        layout.setCellPadding(5, 0, new Insets(0, 72, 0, 0));
        layout.setCellPadding(6, 0, new Insets(0, 72, 0, 0));
        JPanel panel = new JPanel(layout);

        panel.setBorder(BorderFactory.createTitledBorder("Land Flag Settings"));

        landsatLandFlagApplyNdviFilterCheckBox =
                new JCheckBox("NDVI flag (set if NDVI < NDVIT, with NDVI = (TM4 - TM3)/(TM4 + TM3))");
        landsatLandFlagApplyNdviFilterCheckBox.setSelected(true);
        panel.add(landsatLandFlagApplyNdviFilterCheckBox);
        panel.add(new JLabel());
        panel.add(new JLabel());

        landNdviThresholdValue = new JFormattedTextField(Double.toString(LandsatConstants.DEFAULT_NDVI_LAND_THRESHOLD));
        panel.add(new JLabel("NDVI threshold: "));
        panel.add(landNdviThresholdValue);
        panel.add(new JLabel());

        landsatLandFlagApplyTemperatureFilterCheckBox =
                new JCheckBox("Temperature flag (set if TM6 > TM6T (summer), TM6 < TM6T (winter))");
        landsatLandFlagApplyTemperatureFilterCheckBox.setSelected(true);
        panel.add(landsatLandFlagApplyTemperatureFilterCheckBox);
        panel.add(new JLabel());
        panel.add(new JLabel());

        landTM6ThresholdValue = new JFormattedTextField(Double.toString(LandsatConstants.DEFAULT_TM6_LAND_THRESHOLD));
        panel.add(new JLabel("Temperature threshold TM6T (K): "));
        panel.add(landTM6ThresholdValue);
        panel.add(new JLabel());

        panel.add(new JLabel("Season:"));
        panel.add(new JLabel(""));
        panel.add(new JLabel(""));

        landsatSummerButton = new JRadioButton(LandsatConstants.LAND_FLAGS_SUMMER);
        landsatSummerButton.setSelected(true);
        panel.add(landsatSummerButton);
        panel.add(new JLabel(""));
        panel.add(new JLabel(""));
        landsatWinterButton = new JRadioButton(LandsatConstants.LAND_FLAGS_WINTER);
        landsatWinterButton.setSelected(false);
        panel.add(landsatWinterButton);
        panel.add(new JLabel(""));
        panel.add(new JLabel(""));

        landsatSeasonGroup = new ButtonGroup();
        landsatSeasonGroup.add(landsatSummerButton);
        landsatSeasonGroup.add(landsatWinterButton);

        ActionListener landsatSeasonListener = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                updateLandsatSeasonSettings();
            }
        };
        landsatSummerButton.addActionListener(landsatSeasonListener);
        landsatWinterButton.addActionListener(landsatSeasonListener);

        return panel;
    }

    private JPanel createLandsatProcessingPanel() {
        TableLayout layout = new TableLayout(3);
        layout.setTableAnchor(TableLayout.Anchor.WEST);
        layout.setTableFill(TableLayout.Fill.HORIZONTAL);
        layout.setColumnWeightX(0, 0.0);
        layout.setColumnWeightX(1, 1.0);
        layout.setColumnWeightX(2, 0.0);
        layout.setTablePadding(2, 2);

        layout.setCellPadding(0, 0, new Insets(0, 24, 0, 0));
        layout.setCellPadding(1, 0, new Insets(10, 24, 0, 0));
        layout.setCellPadding(2, 0, new Insets(0, 48, 0, 0));
        layout.setCellPadding(3, 0, new Insets(0, 48, 0, 0));
        layout.setCellPadding(4, 0, new Insets(0, 48, 0, 0));
        layout.setCellPadding(5, 0, new Insets(10, 24, 0, 0));
        layout.setCellPadding(6, 0, new Insets(0, 48, 0, 0));
        layout.setCellPadding(7, 0, new Insets(0, 48, 0, 0));

        JPanel panel = new JPanel(layout);

        panel.setBorder(BorderFactory.createTitledBorder("Processing"));

        panel.add(new JLabel("Output products directory : "));
        landsatOutputProductsDir = new JFormattedTextField("");
        panel.add(landsatOutputProductsDir);
        landsatOutputProductsDir.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updateIOTargetProductDir();
            }


            @Override
            public void removeUpdate(DocumentEvent e) {
                updateIOTargetProductDir();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                updateIOTargetProductDir();
            }
        });
        panel.add(new JLabel(""));

        panel.add(new JLabel("Output product type:"));
        panel.add(new JLabel(""));
        panel.add(new JLabel(""));

        landsatOutputProductTypeDownscaleButton = new JRadioButton(
                "Downscale source product to AE correction grid");
        landsatOutputProductTypeDownscaleButton.setSelected(false);
        panel.add(landsatOutputProductTypeDownscaleButton);
        panel.add(new JLabel(""));
        panel.add(new JLabel(""));
        landsatOutputProductTypeFullAEButton = new JRadioButton("Compute AE corrected product on AE correction grid");
        landsatOutputProductTypeFullAEButton.setSelected(true);
        panel.add(landsatOutputProductTypeFullAEButton);
        panel.add(new JLabel(""));
        panel.add(new JLabel(""));
        landsatOutputProductTypeUpscaleAEButton = new JRadioButton("Upscale AE corrected product to original grid");
        landsatOutputProductTypeUpscaleAEButton.setSelected(true);
        panel.add(landsatOutputProductTypeUpscaleAEButton);
        panel.add(new JLabel(""));
        panel.add(new JLabel(""));
        landsatOutputProductTypeFlagsButton = new JRadioButton("Flag bands only");
        landsatOutputProductTypeFlagsButton.setSelected(false);
        // hide this option for the moment...
//        panel.add(landsatOutputProductTypeFlagsButton);
//        panel.add(new JLabel(""));
//        panel.add(new JLabel(""));

        landsatOutputProductTypeGroup = new ButtonGroup();
        landsatOutputProductTypeGroup.add(landsatOutputProductTypeDownscaleButton);
        landsatOutputProductTypeGroup.add(landsatOutputProductTypeFullAEButton);
        landsatOutputProductTypeGroup.add(landsatOutputProductTypeUpscaleAEButton);
        landsatOutputProductTypeGroup.add(landsatOutputProductTypeFlagsButton);

        ActionListener landsatOutputProductTypeListenerListener = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                updateLandsatOutputProductTypeSettings();
            }
        };
        landsatOutputProductTypeFullAEButton.addActionListener(landsatOutputProductTypeListenerListener);
        landsatOutputProductTypeUpscaleAEButton.addActionListener(landsatOutputProductTypeListenerListener);
        landsatOutputProductTypeFlagsButton.addActionListener(landsatOutputProductTypeListenerListener);
        landsatOutputProductTypeDownscaleButton.addActionListener(landsatOutputProductTypeListenerListener);

        panel.add(new JLabel("AE correction grid resolution:"));
        panel.add(new JLabel(""));
        panel.add(new JLabel(""));

        landsatResolution300Button = new JRadioButton("300 m");
        landsatResolution300Button.setSelected(true);
        panel.add(landsatResolution300Button);
        panel.add(new JLabel(""));
        panel.add(new JLabel(""));
        landsatResolution1200Button = new JRadioButton("1200 m");
        landsatResolution1200Button.setSelected(false);
        panel.add(landsatResolution1200Button);
        panel.add(new JLabel(""));
        panel.add(new JLabel(""));

        landsatResolutionGroup = new ButtonGroup();
        landsatResolutionGroup.add(landsatResolution300Button);
        landsatResolutionGroup.add(landsatResolution1200Button);

        ActionListener landsatResolutionListener = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                updateLandsatResolutionSettings();
            }
        };
        landsatResolution300Button.addActionListener(landsatResolutionListener);
        landsatResolution1200Button.addActionListener(landsatResolutionListener);


        return panel;
    }

    private void updateIOTargetProductDir() {
        targetProductSelector.getProductDirTextField().setText(landsatOutputProductsDir.getText());
        targetProductSelector.getModel().getValueContainer().setValue("productDir", new File(landsatOutputProductsDir.getText()));
    }

    private void updateLandsatResolutionSettings() {
        landsatResolution1200Button.setSelected(!landsatResolution300Button.isSelected());
    }

    private void updateLandsatOutputProductTypeSettings() {
        if (landsatOutputProductTypeFullAEButton.isSelected() || landsatOutputProductTypeFlagsButton.isSelected()) {
            landsatOutputProductTypeFlagsButton.setSelected(false);
            landsatOutputProductTypeDownscaleButton.setSelected(false);
        } else {
            landsatOutputProductTypeFullAEButton.setSelected(false);
            landsatOutputProductTypeDownscaleButton.setSelected(false);
        }

        Product sourceProduct = sourceProductSelector.getSelectedProduct();
        final TargetProductSelectorModel selectorModel = targetProductSelector.getModel();
        if (sourceProduct != null) {
            String sourceProductName = sourceProduct.getName();
            updateLandsatProductName(selectorModel, sourceProductName);
        }
    }

    private void updateLandsatSeasonSettings() {
        if (landsatWinterButton.isSelected()) {
            landsatSummerButton.setSelected(false);
        } else {
            landsatWinterButton.setSelected(false);
        }
    }

    private JPanel createProductTypePanel() {
        TableLayout layout = new TableLayout(1);
        layout.setTableAnchor(TableLayout.Anchor.WEST);
        layout.setTableFill(TableLayout.Fill.HORIZONTAL);
        layout.setTableWeightX(1.0);

        JPanel panel = new JPanel(layout);
        panel.setBorder(BorderFactory.createTitledBorder("Product Type Selection"));

        radianceProductTypeButton = new JRadioButton("Compute radiance product");
        radianceProductTypeButton.setSelected(true);
        panel.add(radianceProductTypeButton);
        rhoToaProductTypeButton = new JRadioButton("Compute rhoToa product");
        rhoToaProductTypeButton.setSelected(false);
        panel.add(rhoToaProductTypeButton);

        productTypeGroup = new ButtonGroup();
        productTypeGroup.add(radianceProductTypeButton);
        productTypeGroup.add(rhoToaProductTypeButton);

        ActionListener productTypeListener = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                updateProductTypeSettings();
            }
        };
        rhoToaProductTypeButton.addActionListener(productTypeListener);
        radianceProductTypeButton.addActionListener(productTypeListener);
        return panel;
    }

    private void setRhoToaBandSelectionPanelEnabled(boolean enabled) {
        rhoToaRayleigh.setEnabled(enabled);
        rhoToaAerosol.setEnabled(enabled);
        aeRayleigh.setEnabled(enabled);
        aeAerosol.setEnabled(enabled);
        alphaAot.setEnabled(enabled);
    }

    private void updateProductTypeSettings() {
        setRhoToaBandSelectionPanelEnabled(rhoToaProductTypeButton.isSelected());
        Product sourceProduct = sourceProductSelector.getSelectedProduct();
        final TargetProductSelectorModel selectorModel = targetProductSelector.getModel();
        if (sourceProduct != null) {
            String sourceProductName = sourceProduct.getName();
            final String productType = sourceProduct.getProductType();
            if (productType.toUpperCase().startsWith(LandsatConstants.LANDSAT5_PRODUCT_TYPE_PREFIX) ||
                productType.toUpperCase().startsWith(LandsatConstants.LANDSAT7_PRODUCT_TYPE_PREFIX)) {
                setProductDirChooserVisibility(false);
                targetProductSelector.getProductDirTextField().setText(landsatOutputProductsDir.getText());
                updateLandsatProductName(selectorModel, sourceProductName);
            } else {
                setProductDirChooserVisibility(true);
                if (sourceProductName.endsWith(".N1")) {
                    sourceProductName = sourceProductName.substring(0, sourceProductName.length() - 3);
                }
                if (rhoToaProductTypeButton.isSelected()) {
                    selectorModel.setProductName("L1R_" + sourceProductName);
                } else {
                    selectorModel.setProductName("L1N_" + sourceProductName);
                }
            }
        } else {
            selectorModel.setProductName("icol");
        }
    }

    private void updateLandsatProductName(TargetProductSelectorModel selectorModel, String sourceProductName) {
        if (landsatOutputProductTypeDownscaleButton.isSelected()) {
            selectorModel.setProductName("L1N_" + sourceProductName + LandsatConstants.LANDSAT_DOWNSCALED_PRODUCT_SUFFIX);
            targetProductSelector.getProductNameTextField().setEnabled(false);
        } else if (landsatOutputProductTypeFullAEButton.isSelected()) {
            selectorModel.setProductName("L1N_" + sourceProductName + LandsatConstants.LANDSAT_DOWNSCALED_CORRECTED_PRODUCT_SUFFIX);
            targetProductSelector.getProductNameTextField().setEnabled(false);
        } else if (landsatOutputProductTypeUpscaleAEButton.isSelected()) {
            selectorModel.setProductName("L1N_" + sourceProductName);
            targetProductSelector.getProductNameTextField().setEnabled(true);
        }
    }

    private void setProductDirChooserVisibility(boolean isVisible) {
        targetProductSelector.getProductDirTextField().setVisible(isVisible);
        targetProductSelector.getProductDirChooserButton().setVisible(isVisible);
        targetProductSelector.getProductDirLabel().setVisible(isVisible);
    }

    private void updateProductFormatChange() {
        if (isEnvisatOutputFormatSelected()) {
            JCheckBox saveToFileCheckBox = targetProductSelector.getSaveToFileCheckBox();
            saveToFileCheckBox.setSelected(true);
            saveToFileCheckBox.setEnabled(false);
            targetProductSelector.getFormatNameComboBox().setEnabled(true);
            targetProductSelector.getOpenInAppCheckBox().setEnabled(true);

            radianceProductTypeButton.setSelected(true);
            rhoToaProductTypeButton.setEnabled(false);
        } else {
            targetProductSelector.setEnabled(true);
            rhoToaProductTypeButton.setEnabled(true);
        }
        setRhoToaBandSelectionPanelEnabled(rhoToaProductTypeButton.isSelected());
    }

    void updateParameterMap(Map<String, Object> parameterMap) {
        PropertySet container = bc.getPropertySet();
        for (Property property : container.getProperties()) {
            parameterMap.put(property.getName(), container.getValue(property.getName()));
        }
    }

    public void updateFormModel(Map<String, Object> parameterMap) throws ValidationException, ConversionException {
        Property[] properties = icolContainer.getProperties();
        for (Property property : properties) {
            String propertyName = property.getName();
            Object newValue = parameterMap.get(propertyName);
            if (newValue != null) {
                property.setValue(newValue);
            }
        }
    }

    private static class AeAreaRenderer extends DefaultListCellRenderer {

        @Override
        public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected,
                                                      boolean cellHasFocus) {
            final Component cellRendererComponent =
                    super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

            if (cellRendererComponent instanceof JLabel && value instanceof AeArea) {
                final JLabel label = (JLabel) cellRendererComponent;
                final AeArea aeArea = (AeArea) value;
                label.setText(aeArea.getLabel());
            }

            return cellRendererComponent;
        }
    }
}
