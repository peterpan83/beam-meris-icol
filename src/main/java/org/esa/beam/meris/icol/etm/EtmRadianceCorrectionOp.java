package org.esa.beam.meris.icol.etm;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.dataio.envisat.EnvisatConstants;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.meris.brr.GaseousCorrectionOp;
import org.esa.beam.meris.icol.common.AdjacencyEffectMaskOp;
import org.esa.beam.meris.icol.tm.TmBasisOp;
import org.esa.beam.meris.icol.tm.TmConstants;
import org.esa.beam.meris.icol.utils.IcolUtils;
import org.esa.beam.meris.icol.utils.LandsatUtils;
import org.esa.beam.meris.l2auxdata.Utils;
import org.esa.beam.util.ProductUtils;
import org.esa.beam.util.math.MathUtils;

import javax.media.jai.BorderExtender;
import java.awt.*;

/**
 * @author Olaf Danne
 * @version $Revision: 8078 $ $Date: 2010-01-22 17:24:28 +0100 (Fr, 22 Jan 2010) $
 */
public class EtmRadianceCorrectionOp extends TmBasisOp {
    @SourceProduct(alias = "refl")
    private Product sourceProduct;
    @SourceProduct(alias = "gascor")
    private Product gasCorProduct;
    @SourceProduct(alias = "ae_ray")
    private Product aeRayProduct;
    @SourceProduct(alias = "ae_aerosol")
    private Product aeAerosolProduct;
    @SourceProduct(alias = "aemaskRayleigh")
    private Product aemaskRayleighProduct;
    @SourceProduct(alias = "aemaskAerosol")
    private Product aemaskAerosolProduct;

    @TargetProduct
    private Product targetProduct;

    private double seasonalFactor;

    @Override
    public void initialize() throws OperatorException {

        int daysSince2000 = LandsatUtils.getDaysSince2000(sourceProduct.getStartTime().getElemString());
        seasonalFactor = Utils.computeSeasonalFactor(daysSince2000, TmConstants.SUN_EARTH_DISTANCE_SQUARE);

        targetProduct = createCompatibleProduct(sourceProduct, sourceProduct.getName() + "_ICOL", sourceProduct.getProductType());
        targetProduct.setStartTime(sourceProduct.getStartTime());
        targetProduct.setEndTime(sourceProduct.getEndTime());

        copyRadianceBandGroup(sourceProduct, TmConstants.LANDSAT_REFLECTANCE_BAND_PREFIX);
        ProductUtils.copyFlagBands(sourceProduct, targetProduct, true);
    }

    private void copyRadianceBandGroup(Product srcProduct, String prefix) {
        for (String bandName : srcProduct.getBandNames()) {
            if (bandName.startsWith(prefix)) {
                final String bandNumber = bandName.substring(bandName.length() - 1);
                final int bandId = Integer.parseInt(bandNumber) - 1;
                if (!IcolUtils.isIndexToSkip(bandId, new int[]{TmConstants.LANDSAT7_RADIANCE_6a_BAND_INDEX,
                        TmConstants.LANDSAT7_RADIANCE_6b_BAND_INDEX})) {
                    final String radianceBandName = "radiance_" + bandNumber;
                    Band radianceBand = targetProduct.addBand(radianceBandName, ProductData.TYPE_FLOAT32);
                    radianceBand.setSpectralBandIndex(bandId);
                    radianceBand.setNoDataValue(-1);
                } else if (bandId == TmConstants.LANDSAT7_RADIANCE_6a_BAND_INDEX) {
                    final String temperatureBandName = "radiance_" + bandNumber + "a";
                    Band temperatureBand = targetProduct.addBand(temperatureBandName, ProductData.TYPE_FLOAT32);
                    temperatureBand.setSpectralBandIndex(bandId);
                    temperatureBand.setNoDataValue(-1);
                } else if (bandId == TmConstants.LANDSAT7_RADIANCE_6b_BAND_INDEX) {
                    final String temperatureBandName = "radiance_" + bandNumber + "b";
                    Band temperatureBand = targetProduct.addBand(temperatureBandName, ProductData.TYPE_FLOAT32);
                    temperatureBand.setSpectralBandIndex(bandId);
                    temperatureBand.setNoDataValue(-1);
                }
            }
        }
    }

    @Override
    public void computeTile(Band band, Tile targetTile, ProgressMonitor pm) throws OperatorException {

        Rectangle rectangle = targetTile.getRectangle();
        pm.beginTask("Processing frame...", rectangle.height);
        try {
            String bandName = band.getName();

            final int bandNumber = band.getSpectralBandIndex() + 1;

            Tile szaTile = getSourceTile(sourceProduct.getTiePointGrid(EnvisatConstants.MERIS_SUN_ZENITH_DS_NAME), rectangle,
                                         BorderExtender.createInstance(BorderExtender.BORDER_COPY));
            Tile gasCorTile = getSourceTile(gasCorProduct.getBand(GaseousCorrectionOp.RHO_NG_BAND_PREFIX + "_" + bandNumber), rectangle,
                                            BorderExtender.createInstance(BorderExtender.BORDER_COPY));
            Tile tgTile = getSourceTile(gasCorProduct.getBand(GaseousCorrectionOp.TG_BAND_PREFIX + "_" + bandNumber), rectangle,
                                        BorderExtender.createInstance(BorderExtender.BORDER_COPY));


            //  write reflectances as output, skip TM 6
            if (bandToWrite(bandName, bandNumber)) {
                Tile aeRayleigh = getSourceTile(aeRayProduct.getBand("rho_aeRay_" + bandNumber), rectangle,
                                                BorderExtender.createInstance(BorderExtender.BORDER_COPY));
                Tile aeAerosol = getSourceTile(aeAerosolProduct.getBand("rho_aeAer_" + bandNumber), rectangle,
                                               BorderExtender.createInstance(BorderExtender.BORDER_COPY));
                Tile aepRayleigh = getSourceTile(aemaskRayleighProduct.getBand(AdjacencyEffectMaskOp.AE_MASK_RAYLEIGH), rectangle,
                                                 BorderExtender.createInstance(BorderExtender.BORDER_COPY));
                Tile aepAerosol = getSourceTile(aemaskAerosolProduct.getBand(AdjacencyEffectMaskOp.AE_MASK_AEROSOL), rectangle,
                                                BorderExtender.createInstance(BorderExtender.BORDER_COPY));
                // all reflectances besides TM6 (same for L5 and L7)
                Tile reflectanceR = getSourceTile(sourceProduct.getBand(TmConstants.LANDSAT_REFLECTANCE_BAND_PREFIX + "_tm" + bandNumber),
                                                  rectangle, BorderExtender.createInstance(BorderExtender.BORDER_COPY));
                for (int y = rectangle.y; y < rectangle.y + rectangle.height; y++) {
                    for (int x = rectangle.x; x < rectangle.x + rectangle.width; x++) {
                        double result = 0.0;
                        double gasCorValue = gasCorTile.getSampleDouble(x, y);
                        final double sza = szaTile.getSampleFloat(x, y);
                        final double cosSza = Math.cos(sza * MathUtils.DTOR);
                        if (aepRayleigh.getSampleInt(x, y) == 1 && gasCorValue != -1) {
                            double tgValue = tgTile.getSampleDouble(x, y);
                            final double aeRayleighValue = aeRayleigh.getSampleDouble(x, y);
                            double corrected = gasCorValue - aeRayleighValue;
                            if (aepAerosol.getSampleInt(x, y) == 1) {
                                final double aeAerosolValue = aeAerosol.getSampleDouble(x, y);
                                corrected -= aeAerosolValue;
                            }
                            if (corrected > 0) {
                                double reflectance = corrected * tgValue;
                                result = LandsatUtils.convertReflToRad(reflectance, cosSza, bandNumber - 1, seasonalFactor);
                            }
                        }
                        if (result == 0.0) {
                            double reflectance = reflectanceR.getSampleDouble(x, y);
                            result = LandsatUtils.convertReflToRad(reflectance, cosSza, bandNumber - 1, seasonalFactor);
                        }
                        targetTile.setSample(x, y, result);
                    }
                    pm.worked(1);
                }
            } else if (copyTm6ab(bandNumber)) {
                // Landsat7: copy TM6a, TM6b
                Tile reflectanceR6a = getSourceTile(sourceProduct.getBand
                        (TmConstants.LANDSAT7_REFLECTANCE_BAND_NAMES[TmConstants.LANDSAT7_RADIANCE_6a_BAND_INDEX]),
                                                    rectangle, BorderExtender.createInstance(BorderExtender.BORDER_COPY));
                Tile reflectanceR6b = getSourceTile(sourceProduct.getBand
                        (TmConstants.LANDSAT7_REFLECTANCE_BAND_NAMES[TmConstants.LANDSAT7_RADIANCE_6b_BAND_INDEX]),
                                                    rectangle, BorderExtender.createInstance(BorderExtender.BORDER_COPY));
                if (bandNumber == TmConstants.LANDSAT7_RADIANCE_6a_BAND_INDEX + 1) {
                    for (int y = rectangle.y; y < rectangle.y + rectangle.height; y++) {
                        for (int x = rectangle.x; x < rectangle.x + rectangle.width; x++) {
                            double temperatureTM6a = reflectanceR6a.getSampleDouble(x, y);
                            targetTile.setSample(x, y, temperatureTM6a);
                        }
                    }
                }
                if (bandNumber == TmConstants.LANDSAT7_RADIANCE_6b_BAND_INDEX + 1) {
                    for (int y = rectangle.y; y < rectangle.y + rectangle.height; y++) {
                        for (int x = rectangle.x; x < rectangle.x + rectangle.width; x++) {
                            double temperatureTM6b = reflectanceR6b.getSampleDouble(x, y);
                            targetTile.setSample(x, y, temperatureTM6b);
                        }
                    }
                }
            }
        } catch (Exception e) {
            throw new OperatorException(e);
        } finally {
            pm.done();
        }
    }

    private boolean copyTm6ab(int bandNumber) {
        return sourceProduct.getProductType().startsWith("Landsat7") &&
                (bandNumber == TmConstants.LANDSAT7_RADIANCE_6a_BAND_INDEX + 1 ||
                        bandNumber == TmConstants.LANDSAT7_RADIANCE_6b_BAND_INDEX + 1);
    }

    private boolean bandToWrite(String bandName, int bandNumber) {
        return bandName.startsWith(TmConstants.LANDSAT_RADIANCE_BAND_PREFIX) &&
                !IcolUtils.isIndexToSkip(bandNumber - 1,
                                         new int[]{TmConstants.LANDSAT7_RADIANCE_6a_BAND_INDEX,
                                                 TmConstants.LANDSAT7_RADIANCE_6b_BAND_INDEX});
    }

    public static class Spi extends OperatorSpi {
        public Spi() {
            super(EtmRadianceCorrectionOp.class);
        }
    }

}