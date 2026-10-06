package com.ilot.ilotbackend.domain;
import jakarta.persistence.*;
@Entity @Table(name="equipment_resources") @PrimaryKeyJoinColumn(name="resource_id")
public class EquipmentResource extends Resource {
    @Column(nullable=false) private Integer quantity=1;
    public EquipmentResource() { setType(ResourceType.EQUIPMENT); }
    public Integer getQuantity(){return quantity;} public void setQuantity(Integer v){quantity=v;}
}
