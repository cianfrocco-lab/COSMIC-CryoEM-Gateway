package edu.sdsc.globusauth.model;

public class FileMetadata {
    private String name;
    private String type;
    private Integer size;

    public FileMetadata(String name,
                        String type,
                        Integer size)
    {
        this.name = name;
        this.type = type;
        this.size = size;
    }

    public void setName(String name) {
        this.name = name;
    }
    public String getName(){
        return name;
    }
    public void setType(String type) {
        this.type = type;
    }
    public String getType(){
        return type;
    }
    public void setSize(Integer size) {
        this.size = size;
    }
    public Integer getSize() {
        return size;
    }
}
