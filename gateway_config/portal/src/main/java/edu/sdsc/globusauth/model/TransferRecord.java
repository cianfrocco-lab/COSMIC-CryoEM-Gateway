package edu.sdsc.globusauth.model;

/**
 * Created by cyoun on 10/11/16.
 */
import java.io.Serializable;
import javax.persistence.*;
import javax.persistence.criteria.CriteriaBuilder;

/*
CREATE TABLE `transfer_record` (
  `TR_ID` bigint(20) NOT NULL AUTO_INCREMENT,
  `USER_ID` bigint(20) NOT NULL,
  `TASK_ID` varchar(255) NOT NULL,
  `SRC_ENDPOINTNAME` varchar(255),
  `DEST_ENDPOINTNAME` varchar(255),
  `REQUEST_TIME` varchar(255),
  `COMPLETION_TIME` varchar(255),
  `STATUS` varchar(20),
  `FILES_TRANSFERRED` int(5),
  `SYNC_LEVEL` int(1),
  `FAULTS` int(5),
  `DIRECTORIES` int(5),
  `FILES` int(5),
  `FILES_SKIPPED` int(5),
  `BYTE_TRANSFERRED` bigint(20),
  PRIMARY KEY (`TR_ID`),
  UNIQUE KEY `TASK_ID` (`TASK_ID`),
  KEY `USER_ID` (`USER_ID`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8;
*/

@Entity
@Table(name = "transfer_record")
public class TransferRecord implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "TR_ID", unique = true, nullable = false)
    private Long id;

    @Column(name="USER_ID")
    private Long userId;

    @Column(name="TASK_ID")
    private String taskId;

    @Column(name="SRC_ENDPOINTNAME")
    private String srcEndpointname;

    @Column(name="DEST_ENDPOINTNAME")
    private String destEndpointname;

    @Column(name = "REQUEST_TIME")
    private String requestTime;

    @Column(name = "COMPLETION_TIME")
    private String completionTime;

    @Column(name = "STATUS")
    private String status;

    @Column(name = "FILES_TRANSFERRED")
    private Integer filesTransferred;

    @Column(name = "SYNC_LEVEL")
    private Integer syncLevel;

    @Column(name = "FAULTS")
    private Integer faults;

    @Column(name = "DIRECTORIES")
    private Integer directories;

    @Column(name = "FILES")
    private Integer files;

    @Column(name = "FILES_SKIPPED")
    private Integer filesSkipped;

    @Column(name = "BYTE_TRANSFERRED")
    private Long byteTransferred;

    public Long getId() {
        return id;
    }
    public Long getUserId() {
        return userId;
    }
    public String getTaskId() {
        return taskId;
    }
    public String getSrcEndpointname() {
        return srcEndpointname;
    }
    public String getDestEndpointname() {
        return destEndpointname;
    }
    public String getRequestTime() {return requestTime;}
    public String getCompletionTime() {return completionTime;}
    public String getStatus() {
        return status;
    }
    public Integer getFilesTransferred() { return filesTransferred;}
    public Integer getSyncLevel() { return syncLevel;}
    public Integer getFaults() { return faults;}
    public Integer getDirectories() { return directories;}
    public Integer getFiles() { return files;}
    public Integer getFilesSkipped() {return filesSkipped;}
    public Long getByteTransferred() { return byteTransferred;}

    public void setId(Long id) {
        this.id = id;
    }
    public void setUserId(Long userId) {
        this.userId = userId;
    }
    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }
    public void setSrcEndpointname(String srcEndpointname) {
        this.srcEndpointname = srcEndpointname;
    }
    public void setDestEndpointname(String destEndpointname) {this.destEndpointname = destEndpointname;}
    public void setRequestTime(String requestTime) {this.requestTime = requestTime;}
    public void setCompletionTime(String completionTime) {
        this.completionTime = completionTime;
    }
    public void setStatus(String status) {
        this.status = status;
    }
    public void setFilesTransferred(Integer filesTransferred) { this.filesTransferred = filesTransferred;}
    public void setSyncLevel(Integer syncLevel) { this.syncLevel = syncLevel; }
    public void setFaults(Integer faults) { this.faults = faults; }
    public void setDirectories(Integer directories) { this.directories = directories; }
    public void setFiles(Integer files) { this.files = files; }
    public void setFilesSkipped(Integer filesSkipped) { this.filesSkipped = filesSkipped; }
    public void setByteTransferred(Long byteTransferred) { this.byteTransferred = byteTransferred;}
}

