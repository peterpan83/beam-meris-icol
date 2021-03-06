package org.esa.beam.meris.icol.landsat.tm;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.dataio.envisat.EnvisatConstants;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.meris.brr.GaseousCorrectionOp;
import org.esa.beam.meris.icol.common.AdjacencyEffectMaskOp;
import org.esa.beam.meris.icol.landsat.common.LandsatConstants;
import org.esa.beam.meris.icol.utils.IcolUtils;
import org.esa.beam.meris.icol.utils.LandsatUtils;
import org.esa.beam.meris.l2auxdata.Utils;
import org.esa.beam.util.ProductUtils;
import org.esa.beam.util.math.MathUtils;

import javax.media.jai.BorderExtender;
import java.awt.*;

/**
 *
 * Class providing correction of radiances for Landsat 5 TM
 *
 * @author Olaf Danne
 */
@OperatorMetadata(alias = "Landsat5.Tm.RadianceCorrection",
                  version = "1.0",
                  internal = true,
                  authors = "Olaf Danne",
                  copyright = "(c) 2009 by Brockmann Consult",
                  description = "Landsat 5 TM correction of radiances.")
public class TmRadianceCorrectionOp extends TmBasisOp {
    @SourceProduct(alias="refl")
    private Product sourceProduct;
    @SourceProduct(alias="gascor")
    private Product gasCorProduct;
    @SourceProduct(alias="ae_ray")
    private Product aeRayProduct;
    @SourceProduct(alias="ae_aerosol")
    private Product aeAerosolProduct;
    @SourceProduct(alias="aemaskRayleigh")
    private Product aemaskRayleighProduct;
    @SourceProduct(alias="aemaskAerosol")
    private Product aemaskAerosolProduct;

    @TargetProduct
    private Product targetProduct;

    private double seasonalFactor;

    @Override
    public void initialize() throws OperatorException {

        int daysSince2000 = LandsatUtils.getDaysSince2000(sourceProduct.getStartTime().getElemString());
        seasonalFactor = Utils.computeSeasonalFactor(daysSince2000,  LandsatConstants.SUN_EARTH_DISTANCE_SQUARE);

        targetProduct = createCompatibleProduct(sourceProduct, sourceProduct.getName() + "_ICOL", sourceProduct.getProductType());
        targetProduct.setStartTime(sourceProduct.getStartTime());
        targetProduct.setEndTime(sourceProduct.getEndTime());

        copyRadianceBandGroup(sourceProduct, LandsatConstants.LANDSAT_REFLECTANCE_BAND_PREFIX);
        ProductUtils.copyFlagBands(sourceProduct, targetProduct, true);
    }

    private void copyRadianceBandGroup(Product srcProduct, String prefix) {
        for (String bandName : srcProduct.getBandNames()) {
            if (bandName.startsWith(prefix)) {
                final String bandNumber = bandName.substring(bandName.length() - 1);
                final int bandId = Integer.parseInt(bandNumber) - 1;
                if (!IcolUtils.isIndexToSkip(bandId,
                                            new int[]{LandsatConstants.LANDSAT5_RADIANCE_6_BAND_INDEX})) {
                    String radianceBandName = "radiance_" + bandNumber;
                    Band radianceBand = targetProduct.addBand(radianceBandName, ProductData.TYPE_FLOAT32);
                    radianceBand.setSpectralBandIndex(bandId);
                    radianceBand.setNoDataValue(-1);
                }  else if (bandId == LandsatConstants.LANDSAT5_RADIANCE_6_BAND_INDEX) {
                    String temperatureBandName = "radiance_" + bandNumber;
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
            if (bandName.startsWith(LandsatConstants.LANDSAT_RADIANCE_BAND_PREFIX) &&
                    !IcolUtils.isIndexToSkip(bandNumber - 1,
                                            new int[]{LandsatConstants.LANDSAT5_RADIANCE_6_BAND_INDEX})) {
                Tile aeRayleigh = getSourceTile(aeRayProduct.getBand("rho_aeRay_" + bandNumber), rectangle,
                        BorderExtender.createInstance(BorderExtender.BORDER_COPY));
                Tile aeAerosol = getSourceTile(aeAerosolProduct.getBand("rho_aeAer_" + bandNumber), rectangle,
                        BorderExtender.createInstance(BorderExtender.BORDER_COPY));
                Tile aepRayleigh = getSourceTile(aemaskRayleighProduct.getBand(AdjacencyEffectMaskOp.AE_MASK_RAYLEIGH), rectangle,
                        BorderExtender.createInstance(BorderExtender.BORDER_COPY));
                Tile aepAerosol = getSourceTile(aemaskAerosolProduct.getBand(AdjacencyEffectMaskOp.AE_MASK_AEROSOL), rectangle,
                        BorderExtender.createInstance(BorderExtender.BORDER_COPY));
                Tile reflectanceR = getSourceTile(sourceProduct.getBand(LandsatConstants.LANDSAT_REFLECTANCE_BAND_PREFIX + "_tm" + bandNumber),
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
                                result = LandsatUtils.convertReflToRadLandsat5(reflectance, cosSza, bandNumber - 1, seasonalFactor);
                            }
                        }
                        if (result == 0.0) {
                            double reflectance  = reflectanceR.getSampleDouble(x, y);
                            result = LandsatUtils.convertReflToRadLandsat5(reflectance, cosSza, bandNumber - 1, seasonalFactor);
                        }
                        targetTile.setSample(x, y, result);
                    }
                    pm.worked(1);
                }
            } else if (bandNumber == LandsatConstants.LANDSAT5_RADIANCE_6_BAND_INDEX + 1) {
                // just copy TM6
                Tile reflectanceR = getSourceTile(sourceProduct.getBand
                        (LandsatConstants.LANDSAT5_REFLECTANCE_BAND_NAMES[LandsatConstants.LANDSAT5_RADIANCE_6_BAND_INDEX]),
                                                  rectangle, BorderExtender.createInstance(BorderExtender.BORDER_COPY));
                for (int y = rectangle.y; y < rectangle.y + rectangle.height; y++) {
                    for (int x = rectangle.x; x < rectangle.x + rectangle.width; x++) {
                        double temperatureTM6  = reflectanceR.getSampleDouble(x, y);
                        targetTile.setSample(x, y, temperatureTM6);
                    }
                }
            }
        } catch (Exception e) {
            throw new OperatorException(e);
        } finally {
            pm.done();
        }
    }


    public static class Spi extends OperatorSpi {
        public Spi() {
            super(TmRadianceCorrectionOp.class);
        }
    }

}
