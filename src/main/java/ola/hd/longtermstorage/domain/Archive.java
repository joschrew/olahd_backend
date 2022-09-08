package ola.hd.longtermstorage.domain;

import java.util.ArrayList;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "archive")
public class Archive {

    @Id
    private String id;

    // PID of the archive
    private String pid;

    // CDSTAR-ID of an online archive
    private String onlineId;

    // CDSTAR-ID of an offline archive
    private String offlineId;

    @DBRef(lazy = true)
    private Archive previousVersion;

    @DBRef(lazy = true)
    private List<Archive> nextVersions;

    /** file-group (from mets) used for indexing (searchindex) the images */
    private String imageFileGrp;

    /**
     * file-group (from mets) used for indexing (searchindex) the fulltexts
     */
    private String fulltextFileGrp;

    protected Archive() {
        // no-args constructor required by JPA spec
        // this one is protected since it shouldn't be used directly
    }

    public Archive(String pid, String onlineId, String offlineId) {
        this.pid = pid;
        this.onlineId = onlineId;
        this.offlineId = offlineId;
    }

    public void addNextVersion(Archive nextVersion) {
        if (nextVersions == null) {
            nextVersions = new ArrayList<>();
        }
        nextVersions.add(nextVersion);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPid() {
        return pid;
    }

    public void setPid(String pid) {
        this.pid = pid;
    }

    public String getOnlineId() {
        return onlineId;
    }

    public void setOnlineId(String onlineId) {
        this.onlineId = onlineId;
    }

    public String getOfflineId() {
        return offlineId;
    }

    public void setOfflineId(String offlineId) {
        this.offlineId = offlineId;
    }

    public Archive getPreviousVersion() {
        return previousVersion;
    }

    public void setPreviousVersion(Archive previousVersion) {
        this.previousVersion = previousVersion;
    }

    public List<Archive> getNextVersions() {
        return nextVersions;
    }

    public void setNextVersions(List<Archive> nextVersions) {
        this.nextVersions = nextVersions;
    }

    public String getImageFileGrp() {
        return imageFileGrp;
    }

    public void setImageFileGrp(String imageFileGrp) {
        this.imageFileGrp = imageFileGrp;
    }

    public String getFulltextFileGrp() {
        return fulltextFileGrp;
    }

    public void setFulltextFileGrp(String fulltextFileGrp) {
        this.fulltextFileGrp = fulltextFileGrp;
    }
}
