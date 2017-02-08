package edu.sdsc.globusauth.model;

/**
 * Created by cyoun on 10/12/16.
 */

/*
CREATE TABLE `users` (
  `USER_ID` bigint(20) NOT NULL AUTO_INCREMENT,
  `DEFAULT_GROUP_ID` bigint(20) DEFAULT NULL,
  `COMMENT` varchar(255) DEFAULT NULL,
  `INSTITUTION` varchar(255) DEFAULT NULL,
  `AREA_CODE` varchar(10) DEFAULT NULL,
  `CITY` varchar(100) DEFAULT NULL,
  `COUNTRY` varchar(50) DEFAULT NULL,
  `EMAIL` varchar(200) NOT NULL,
  `FIRST_NAME` varchar(100) NOT NULL,
  `LAST_NAME` varchar(100) NOT NULL,
  `MAILCODE` varchar(10) DEFAULT NULL,
  `PASSWORD` varchar(50) NOT NULL,
  `PHONE_NUMBER` varchar(20) DEFAULT NULL,
  `ROLE` varchar(50) NOT NULL,
  `STATE` varchar(50) DEFAULT NULL,
  `STREET_ADDRESS` varchar(255) DEFAULT NULL,
  `USERNAME` varchar(200) NOT NULL,
  `WEBSITE_URL` varchar(255) DEFAULT NULL,
  `ZIP_CODE` varchar(10) DEFAULT NULL,
  `ACTIVE` bit(1) NOT NULL DEFAULT b'1',
  `VERSION` int(11) NOT NULL DEFAULT '0',
  `CAN_SUBMIT` bit(1) NOT NULL DEFAULT b'1',
  `LAST_LOGIN` datetime DEFAULT NULL,
  `UMBRELLA_APPNAME` varchar(30) NOT NULL DEFAULT '',
  `ACTIVATION_CODE` varchar(50) DEFAULT NULL,
  `ACTIVATION_SENT` datetime DEFAULT NULL,
  `DATE_CREATED` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`USER_ID`),
  UNIQUE KEY `USERNAME` (`USERNAME`),
  UNIQUE KEY `EMAIL` (`EMAIL`,`ROLE`,`UMBRELLA_APPNAME`),
  KEY `DEFAULT_GROUP_ID` (`DEFAULT_GROUP_ID`),
  KEY `ACTIVATION` (`ACTIVATION_SENT`)
) ENGINE=InnoDB AUTO_INCREMENT=91983 DEFAULT CHARSET=latin1;
 */

import org.hibernate.annotations.Type;

import java.io.Serializable;
import java.util.Date;
import java.sql.Timestamp;

import javax.persistence.*;

@Entity
@Table(name = "users")
public class User implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "USER_ID", unique = true, nullable = false)
    private Long id;

    @Column(name = "DEFAULT_GROUP_ID")
    private Long defaultGroupId;

    @Column(name = "COMMENT")
    private String comment;

    @Column(name = "INSTITUTION")
    private String institution;

    @Column(name = "AREA_CODE")
    private String areaCode;

    @Column(name = "CITY")
    private String city;

    @Column(name = "COUNTRY")
    private String country;

    @Column(name = "EMAIL", unique = true, nullable = false)
    private String email;

    @Column(name = "FIRST_NAME", nullable = false)
    private String firstName;

    @Column(name = "LAST_NAME", nullable = false)
    private String lastName;

    @Column(name = "MAILCODE")
    private String mailcode;

    @Column(name = "PASSWORD")
    private String password;

    @Column(name = "PHONE_NUMBER")
    private String phoneNumber;

    @Column(name = "ROLE", nullable = false)
    private String role;

    @Column(name = "STATE")
    private String state;

    @Column(name = "STREET_ADDRESS")
    private String streetAddress;

    @Column(name = "USERNAME", nullable = false)
    private String username;

    @Column(name = "WEBSITE_URL")
    private String websiteUrl;

    @Column(name = "ZIP_CODE")
    private String zipCode;

    @Type(type = "numeric_boolean")
    //@Column(name = "ACTIVE", columnDefinition = "BIT", nullable = false)
    @Column(name = "ACTIVE", columnDefinition = "BIT")
    private Boolean active;

    @Column(name = "VERSION")
    private Integer version;

    @Type(type = "numeric_boolean")
    @Column(name = "CAN_SUBMIT",columnDefinition = "BIT")
    private Boolean canSubmit;

    @Column(name = "LAST_LOGIN", columnDefinition="DATETIME")
    @Temporal(TemporalType.TIMESTAMP)
    private Date lastLogin;

    @Column(name = "UMBRELLA_APPNAME")
    private String umbrellaAppname;

    @Column(name = "ACTIVATION_CODE")
    private String activationCode;

    @Column(name = "ACTIVATION_SENT",columnDefinition = "DATETIME")
    @Temporal(TemporalType.TIMESTAMP)
    private Date activationSent;

    @Column(name = "DATE_CREATED")
    private Timestamp dateCreated;

    public Long getId() {return id;}
    public Long getDefaultGroupId() { return defaultGroupId; }
    public Integer getVersion() {return version;}
    public Date getLastLogin() {return lastLogin;}
    public Date getActivationSent() {return activationSent;}
    public Timestamp getDateCreated() {return dateCreated;}
    public Boolean getActive() {return active;}
    public Boolean getCanSubmit() {return canSubmit;}
    public String getComment() {return comment;}
    public String getInstitution() {return institution;}
    public String getAreaCode() {return areaCode;}
    public String getCity() {return city;}
    public String getCountry() {return country;}
    public String getEmail() {return email;}
    public String getFirstName() {return firstName;}
    public String getLastName() {return lastName;}
    public String getMailcode() {return mailcode;}
    public String getPassword() {return password;}
    public String getPhoneNumber() {return phoneNumber;}
    public String getRole() {return role;}
    public String getState() {return state;}
    public String getStreetAddress() {return streetAddress;}
    public String getUsername() {return username;}
    public String getWebsiteUrl() {return websiteUrl;}
    public String getZipCode() {return zipCode;}
    public String getUmbrellaAppname() {return umbrellaAppname;}
    public String getActivationCode() {return activationCode;}

    public void setId(Long id) {this.id = id;}
    public void setDefaultGroupId(Long defaultGroupId) {this.defaultGroupId = defaultGroupId;}
    public void setComment(String comment) {this.comment = comment;}
    public void setInstitution(String institution) {this.institution = institution;}
    public void setAreaCode(String areaCode) {this.areaCode = areaCode;}
    public void setCity(String city) {this.city = city;}
    public void setCountry(String country) {this.country = country;}
    public void setEmail(String email) {this.email = email;}
    public void setFirstName(String firstName) {this.firstName = firstName;}
    public void setLastName(String lastName) {this.lastName = lastName;}
    public void setMailcode(String mailcode) {this.mailcode = mailcode;}
    public void setPassword(String password) {this.password = password;}
    public void setPhoneNumber(String phoneNumber) {this.phoneNumber = phoneNumber;}
    public void setRole(String role) {this.role = role;}
    public void setState(String state) {this.state = state;}
    public void setStreetAddress(String streetAddress) {this.streetAddress = streetAddress;}
    public void setUsername(String username) {this.username = username;}
    public void setWebsiteUrl(String websiteUrl) {this.websiteUrl = websiteUrl;}
    public void setZipCode(String zipCode) {this.zipCode = zipCode;}
    public void setActive(Boolean active) {this.active = active;}
    public void setVersion(Integer version) {this.version = version;}
    public void setCanSubmit(Boolean canSubmit) {this.canSubmit = canSubmit;}
    public void setLastLogin(Date lastLogin) {this.lastLogin = lastLogin;}
    public void setUmbrellaAppname(String umbrellaAppname) {this.umbrellaAppname = umbrellaAppname;}
    public void setActivationCode(String activationCode) {this.activationCode = activationCode;}
    public void setActivationSent(Date activationSent) {this.activationSent = activationSent;}
    public void setDateCreated(Timestamp dateCreated) {this.dateCreated = dateCreated;}

}
