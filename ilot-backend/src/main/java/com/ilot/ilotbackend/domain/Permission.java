package com.ilot.ilotbackend.domain;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
@Entity @Table(name="permissions")
public class Permission extends BaseEntity {
    @NotBlank @Column(name="permission_key",nullable=false,unique=true,length=100) private String key;
    public Permission() {}
    public Permission(String key){this.key=key;}
    public String getKey(){return key;} public void setKey(String v){key=v;}
}
