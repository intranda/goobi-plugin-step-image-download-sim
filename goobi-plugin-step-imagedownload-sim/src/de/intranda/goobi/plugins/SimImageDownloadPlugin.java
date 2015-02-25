package de.intranda.goobi.plugins;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.config.RequestConfig.Builder;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.log4j.Logger;
import org.goobi.beans.Step;
import org.goobi.production.cli.helper.WikiFieldHelper;
import org.goobi.production.enums.PluginGuiType;
import org.goobi.production.enums.PluginType;
import org.goobi.production.enums.StepReturnValue;
import org.goobi.production.plugin.interfaces.IPlugin;
import org.goobi.production.plugin.interfaces.IStepPlugin;
import org.goobi.beans.Process;

import de.sub.goobi.config.ConfigurationHelper;
import de.sub.goobi.helper.HttpClientHelper;
import de.sub.goobi.helper.exceptions.DAOException;
import de.sub.goobi.helper.exceptions.SwapException;
import de.sub.goobi.persistence.managers.ProcessManager;
import ugh.dl.DocStruct;
import ugh.dl.Fileformat;
import ugh.dl.Metadata;
import ugh.exceptions.PreferencesException;
import ugh.exceptions.ReadException;
import ugh.exceptions.WriteException;
import net.xeoh.plugins.base.annotations.PluginImplementation;

@PluginImplementation
public class SimImageDownloadPlugin implements IStepPlugin, IPlugin {

    private static final Logger logger = Logger.getLogger(SimImageDownloadPlugin.class);

    private static final String PLUGIN_NAME = "SimImageDownload";

    private Step step;
    private String returnPath;

    @Override
    public PluginType getType() {
        return PluginType.Step;
    }

    @Override
    public String getTitle() {
        return PLUGIN_NAME;
    }

    @Override
    public String getDescription() {
        return PLUGIN_NAME;
    }

    @Override
    public void initialize(Step step, String returnPath) {
        this.step = step;
        this.returnPath = returnPath;
    }

    @Override
    public String cancel() {
        return returnPath;
    }

    @Override
    public String finish() {
        return returnPath;
    }

    @Override
    public HashMap<String, StepReturnValue> validate() {
        return null;
    }

    @Override
    public Step getStep() {
        return step;
    }

    @Override
    public PluginGuiType getPluginGuiType() {
        return PluginGuiType.NONE;
    }

    
    public String getPagePath() {
        return null;
    }

    @Override
    public boolean execute() {
        Process process = step.getProzess();

        try {
            Fileformat ff = process.readMetadataFile();
            DocStruct log = ff.getDigitalDocument().getLogicalDocStruct();
            String downloadFolder = process.getImagesOrigDirectory(false);
            List<? extends Metadata> metadataList =
                    log.getAllMetadataByType(process.getRegelsatz().getPreferences().getMetadataTypeByName("_imageName"));

            if (metadataList != null && !metadataList.isEmpty()) {
                for (Metadata md : metadataList) {
                    String url = md.getValue().trim();
                    if (url.startsWith("http://www.gbv.de/dms/sim-prog/")) {
                        downloadFile(url, downloadFolder);
                    }
                }
            }

        } catch (ReadException | PreferencesException | SwapException | DAOException | WriteException | IOException | InterruptedException e) {
            logger.error(e);
            ProcessManager.addLogfile(WikiFieldHelper.getWikiMessage(process.getWikifield(), "error", e.getMessage()), process.getId());

            return false;
        }
        ProcessManager.addLogfile(WikiFieldHelper.getWikiMessage(process.getWikifield(), "info", "Download der Bilder abgeschlossen."), process.getId());
        return true;
    }

    private void downloadFile(String url, String downloadFolder) throws IOException {
        String[] parts = url.split("/");
        String imgName = parts[parts.length - 1];
        if (imgName.contains(".")) {
            imgName = imgName.substring(0, imgName.indexOf(".")) + imgName.substring(imgName.lastIndexOf("."));
        }

        File imageFile = new File(downloadFolder, imgName);
        imageFile.getParentFile().mkdirs();
        imageFile.createNewFile();

        CloseableHttpClient httpclient = null;
        HttpGet method = null;
        InputStream istr = null;
        OutputStream ostr = null;
        try {
            httpclient = HttpClientBuilder.create().build();
            method = new HttpGet(url);
          

            byte[] response = httpclient.execute(method, HttpClientHelper.byteArrayResponseHandler);
            if (response == null) {
                logger.error("Response stream is null");
                return;
            }
            istr = new ByteArrayInputStream(response);
            
            ostr = new FileOutputStream(imageFile);

            // Transfer bytes from in to out
            byte[] buf = new byte[1024];
            int len;
            while ((len = istr.read(buf)) > 0) {
                ostr.write(buf, 0, len);
            }
       
        } finally {
            method.releaseConnection();
            if (httpclient != null) {
                httpclient.close();
            }
            if (istr != null) {
                istr.close();
            }
            if (ostr != null) {
                ostr.close();
            }
        }

    }
}
