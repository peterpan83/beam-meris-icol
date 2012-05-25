package org.esa.beam.meris.icol.tm;

import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.MetadataAttribute;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.meris.icol.utils.OperatorUtils;
import org.esa.beam.util.ProductUtils;

import javax.media.jai.Interpolation;
import javax.media.jai.RenderedOp;
import javax.media.jai.operator.ScaleDescriptor;
import javax.media.jai.operator.SubtractDescriptor;
import java.awt.image.RenderedImage;

/**
 * Operator for upscaling of TM product from geometry to original resolution after AE correction
 *
 * @author Olaf Danne
 * @version $Revision: 8078 $ $Date: 2010-01-22 17:24:28 +0100 (Fr, 22 Jan 2010) $
 */
public class TmUpscaleToOriginalOp extends TmBasisOp {

    @SourceProduct(alias = "l1b")
    private Product sourceProduct;
    @SourceProduct(alias = "geometry")
    private Product geometryProduct;
    @SourceProduct(alias = "corrected")
    private Product correctedProduct;
    @TargetProduct
    private Product targetProduct;

    @Override
    public void initialize() throws OperatorException {
        final MetadataAttribute widthAttr = sourceProduct.getMetadataRoot().getElement("L1_METADATA_FILE").getElement("PRODUCT_METADATA").getAttribute("PRODUCT_SAMPLES_REF");
        final MetadataAttribute heightAttr = sourceProduct.getMetadataRoot().getElement("L1_METADATA_FILE").getElement("PRODUCT_METADATA").getAttribute("PRODUCT_LINES_REF");

        if (widthAttr == null || heightAttr == null) {
            throw new OperatorException("Cannot upscale to original grid - metadata info missing.");
        }

        final int width = widthAttr.getData().getElemIntAt(0);
        final int height = heightAttr.getData().getElemIntAt(0);

//        float xScale = (float) sourceProduct.getSceneRasterWidth() / correctedProduct.getSceneRasterWidth();
        float xScale = (float) width / correctedProduct.getSceneRasterWidth();
//        float yScale = (float) sourceProduct.getSceneRasterHeight() / correctedProduct.getSceneRasterHeight();
        float yScale = (float) height / correctedProduct.getSceneRasterHeight();
        targetProduct = createTargetProduct(sourceProduct, "upscale_" + correctedProduct.getName(),
                                            sourceProduct.getProductType(),
                                            width, height);

        for (int i = 0; i < sourceProduct.getNumBands(); i++) {
            Band sourceBand = sourceProduct.getBandAt(i);

            Band targetBand;
            int dataType = sourceBand.getDataType();
            final String srcBandName = sourceBand.getName();
            if (srcBandName.startsWith("radiance")) {
                final int length = srcBandName.length();
                final String radianceBandSuffix = srcBandName.substring(length - 1, length);
                final int radianceBandIndex = Integer.parseInt(radianceBandSuffix);

//                this was for debugging only:
//                Band diffBand = null;
//                Band origBand = null;
//                if (radianceBandIndex != 6) {
//                    dataType = ProductData.TYPE_FLOAT32;
//                    diffBand = targetProduct.addBand(srcBandName + "_diff", dataType);
//                    origBand = targetProduct.addBand(srcBandName + "_orig", sourceBand.getDataType());
//                    ProductUtils.copyRasterDataNodeProperties(sourceBand, origBand);
//                }
//                targetBand = targetProduct.addBand(srcBandName, dataType);

                if (radianceBandIndex != 6) {
                    targetBand = targetProduct.addBand(srcBandName, ProductData.TYPE_FLOAT32);
                    Band correctedBand = correctedProduct.getBand(sourceBand.getName());
                    Band geometryBand = geometryProduct.getBand(sourceBand.getName());

                    RenderedImage geometryImage = geometryBand.getSourceImage();
                    RenderedImage correctedImage = correctedBand.getSourceImage();
                    RenderedOp diffImage = SubtractDescriptor.create(geometryImage, correctedImage, null);

                    // here we upscale the difference image (i.e., the AE correction)
                    // note that xscale, yscale may be 1 (in fact no upscaling) if source is a geometry product
                    RenderedOp upscaledDiffImage = ScaleDescriptor.create(diffImage,
                                                                          xScale,
                                                                          yScale,
                                                                          0.0f, 0.0f,
                                                                          Interpolation.getInstance(
                                                                                  Interpolation.INTERP_BILINEAR),
                                                                          null);
//                    this was for debugging only:
//                    origBand.setSourceImage(sourceImage);
//                    diffBand.setSourceImage(upscaledDiffImage);

                    // here we subtract the AE correction on original resolution
                    RenderedOp finalAeCorrectedImage = SubtractDescriptor.create(sourceBand.getGeophysicalImage(), upscaledDiffImage, null);
                    targetBand.setSourceImage(finalAeCorrectedImage);
                } else {
                    targetBand = targetProduct.addBand(srcBandName, dataType);
                    ProductUtils.copyRasterDataNodeProperties(sourceBand, targetBand);
                }
            }
        }

        if (System.getProperty("additionalOutputBands") != null && System.getProperty("additionalOutputBands").equals(
                "RS")) {
            for (int i = 1; i <= 7; i++) {
                if (i != 6) {
                    // these bands only exist in "RS debug mode"
                    upscaleDebugBand("rho_ag_bracket", xScale, yScale, i);
                    upscaleDebugBand("rho_raec_bracket", xScale, yScale, i);
                }
            }
        }

    }

    private static Product createTargetProduct(Product sourceProduct, String name, String type, int width, int height) {

        Product targetProduct = new Product(name, type, width, height);
        OperatorUtils.copyProductBase(sourceProduct, targetProduct);
        return targetProduct;
    }

    private void upscaleDebugBand(String bandName, float xScale, float yScale, int i) {
        Band debugSrcBand = correctedProduct.getBand(bandName + "_" + i);
        Band debugTargetBand = targetProduct.addBand(bandName + "_" + i, ProductData.TYPE_FLOAT32);
        RenderedImage debugSrcImage = debugSrcBand.getSourceImage();
        RenderedOp upscaledDebugSrcImage = ScaleDescriptor.create(debugSrcImage,
                                                                  xScale,
                                                                  yScale,
                                                                  0.0f, 0.0f,
                                                                  Interpolation.getInstance(
                                                                          Interpolation.INTERP_BILINEAR),
                                                                  null);
        debugTargetBand.setSourceImage(upscaledDebugSrcImage);
    }

    public static class Spi extends OperatorSpi {

        public Spi() {
            super(TmUpscaleToOriginalOp.class);
        }
    }
}