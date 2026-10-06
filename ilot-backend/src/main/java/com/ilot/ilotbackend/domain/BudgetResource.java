package com.ilot.ilotbackend.domain;
import jakarta.persistence.*;
import java.math.BigDecimal;
@Entity @Table(name="budget_resources") @PrimaryKeyJoinColumn(name="resource_id")
public class BudgetResource extends Resource {
    @Column(nullable=false,precision=15,scale=2) private BigDecimal amount=BigDecimal.ZERO;
    @Column(nullable=false,length=3) private String currency="USD";
    @Column(nullable=false,precision=15,scale=2) private BigDecimal consumed=BigDecimal.ZERO;
    @Column(nullable=false,precision=15,scale=2) private BigDecimal remaining=BigDecimal.ZERO;
    public BudgetResource(){setType(ResourceType.BUDGET);}
    public BigDecimal getAmount(){return amount;} public void setAmount(BigDecimal v){amount=v;}
    public String getCurrency(){return currency;} public void setCurrency(String v){currency=v;}
    public BigDecimal getConsumed(){return consumed;} public void setConsumed(BigDecimal v){consumed=v;}
    public BigDecimal getRemaining(){return remaining;} public void setRemaining(BigDecimal v){remaining=v;}
}
