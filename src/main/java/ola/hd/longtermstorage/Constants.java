package ola.hd.longtermstorage;

/**
 * Constants for the whole application
 *
 */
public class Constants {
    public final static String LOGICAL_INDEX_NAME = "meta.olahds_log";
    public final static String PHYSICAL_INDEX_NAME = "meta.olahds_phys";
    public static final String DEFAULT_IMAGE_FILEGRP = "OCR-D-IMG";
    public static final String DEFAULT_FULLTEXT_FILEGRP = "OCR-D-GT-SEG-LINE";
    public static final String DEFAULT_FULLTEXT_FTYPE = "PAGEXML_1";

    public static final String BAGINFO_KEY_IMAGE_FILEGRP = "Olahd-Search-Image-Filegrp";
    public static final String BAGINFO_KEY_FULLTEXT_FILEGRP = "Olahd-Search-Fulltext-Filegrp";
    public static final String BAGINFO_KEY_FTYPE = "Olahd-Search-Fulltext-Ftype";
    public static final String BAGINFO_KEY_IS_GT = "Olahd-GT";

    private Constants() {
        //pass
    };
}
