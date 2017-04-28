package edu.sdsc.globusauth.model;

/**
 * Created by cyoun on 10/11/16.
 */
import java.io.Serializable;
import javax.persistence.*;

/*
CREATE TABLE `oauth_profile` (
  `PROFILE_ID` bigint(20) NOT NULL AUTO_INCREMENT,
  `USER_ID` bigint(20) NOT NULL,
  `IDENTITY_ID` varchar(255) NOT NULL,
  `USERNAME` varchar(255) NOT NULL,
  `FIRST_NAME` varchar(100) NOT NULL,
  `LAST_NAME` varchar(100) NOT NULL,
  `EMAIL` varchar(200) NOT NULL,
  `INSTITUTION` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`PROFILE_ID`),
  UNIQUE KEY `IDENTITY_ID` (`IDENTITY_ID`),
  KEY `USER_ID` (`USER_ID`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8;
*/

@Entity
@Table(name = "oauth_profile")
public class OauthProfile implements Serializable {
    private static final long serialVersionUID = -8767337896773261247L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "PROFILE_ID", unique = true, nullable = false)
    private Long id;

    @Column(name="USER_ID")
    private Long userId;

    @Column(name="IDENTITY_ID")
    private String identityId;

    @Column(name="USERNAME")
    private String userName;

    @Column(name="EMAIL")
    private String email;

    @Column(name = "FIRST_NAME", nullable = false)
    private String firstName;

    @Column(name = "LAST_NAME", nullable = false)
    private String lastName;

    @Column(name = "INSTITUTION")
    private String institution;

    public Long getId() {
        return id;
    }
    public Long getUserId() {
        return userId;
    }
    public String getIdentityId() {
        return identityId;
    }
    public String getUserName() {
        return userName;
    }
    public String getEmail() {
        return email;
    }
    public String getFirstName() {return firstName;}
    public String getLastName() {return lastName;}
    public String getInstitution() {
        return institution;
    }

    public void setId(Long id) {
        this.id = id;
    }
    public void setUserId(Long userId) {
        this.userId = userId;
    }
    public void setIdentityId(String identityId) {
        this.identityId = identityId;
    }
    public void setUserName(String userName) {
        this.userName = userName;
    }
    public void setFirstName(String firstName) {this.firstName = firstName;}
    public void setLastName(String lastName) {this.lastName = lastName;}
    public void setEmail(String email) {
        this.email = email;
    }
    public void setInstitution(String institution) {
        this.institution = institution;
    }
}

