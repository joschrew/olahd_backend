package ola.hd.longtermstorage.utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import org.jdom2.Attribute;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.Namespace;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;

/**
 * This class offers functionality to convert the file-locations containing in a Metsfile to
 * web-accessible URL's.
 * Example:
 * <code>
 * <mets:fileGrp USE="OCR-D-IMG">
 *   <mets:file MIMETYPE="image/jpeg" ID="OCR-D-IMG_0001">
 *     <mets:FLocat LOCTYPE="URL" xlink:href="OCR-D-IMG/OCR-D-IMG_0001.jpg"/>
 * </code>
 * will be converted to something like:
 * <code>
 * <mets:fileGrp USE="OCR-D-IMG">
 *   <mets:file MIMETYPE="image/jpeg" ID="OCR-D-IMG_0001">
 *     <mets:FLocat LOCTYPE="URL" xlink:href="http://ola-hd.ocr-d.de/api/export/file?id=21.T11998/0000-001C-9CBE-D&amp;path=OCR-D-IMG/OCR-D-IMG_0001.jpg" />
 * </code>
 */
public class MetsWebConverter {

    /** Path to where the files are available */
    private static String PREFIX = "%s/api/export/file?id=%s&path=%s";

    private MetsWebConverter() {
    }

    /**
     *
     * @param pid - PID belonging to the Ocrd-zip of the Metsfile
     * @param ins - InputStream containing the original Metsfile
     * @param outs - Where to write the converted Metsfile to
     * @throws IOException
     * @throws JDOMException
     * @throws Exception
     */
    public static void convertMets(String pid, String host, InputStream ins, OutputStream outs) throws JDOMException, IOException {
        SAXBuilder sax = new SAXBuilder();
        Document doc = sax.build(ins);
        Element rootNode = doc.getRootElement();

        Namespace nsMets = Namespace.getNamespace("http://www.loc.gov/METS/");
        Namespace nsXlink = Namespace.getNamespace("http://www.w3.org/1999/xlink");
        List<Element> listFileSec = rootNode.getChildren("fileSec", nsMets);
        List<Element> listFileGrp = listFileSec.get(0).getChildren("fileGrp", nsMets);
        List<Element> listFlocat = new ArrayList<>();
        for (Element e : listFileGrp) {
            for (Element e2 : e.getChildren("file", nsMets)) {
                listFlocat.add(e2.getChildren("FLocat", nsMets).get(0));
            }
        }
        for (Element e : listFlocat) {
            Attribute attribute = e.getAttribute("href", nsXlink);
            String linkPrev = attribute.getValue();
            if (!linkPrev.startsWith("http") && !linkPrev.startsWith("/")) {
                e.setAttribute("href", String.format(PREFIX, host, pid, linkPrev), nsXlink);
            }
        }
        XMLOutputter xmlOutput = new XMLOutputter();
        Format format = Format.getPrettyFormat();
        format.setIndent("   ");
        xmlOutput.setFormat(format);
        xmlOutput.output(doc, outs);
    }
}
