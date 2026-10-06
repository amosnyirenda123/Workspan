package com.ilot.ilotbackend.domain;
import jakarta.persistence.*;
@Entity @Table(name="task_dependencies",uniqueConstraints=@UniqueConstraint(columnNames={"predecessor_task_id","successor_task_id"}))
public class TaskDependency extends BaseEntity {
    @ManyToOne(fetch=FetchType.LAZY,optional=false)
    @JoinColumn(name="predecessor_task_id",nullable=false)
    private Task predecessorTask;
    @ManyToOne(fetch=FetchType.LAZY,optional=false)
    @JoinColumn(name="successor_task_id",nullable=false)
    private Task successorTask;
    @Enumerated(EnumType.STRING)
    @Column(nullable=false,length=30)
    private DependencyType type=DependencyType.FINISH_TO_START;
    @Column(nullable=false, name = "lag_days")
    private Integer lag=0;
    public TaskDependency() {}
    public Task getPredecessorTask(){return predecessorTask;} public void setPredecessorTask(Task v){predecessorTask=v;}
    public Task getSuccessorTask(){return successorTask;} public void setSuccessorTask(Task v){successorTask=v;}
    public DependencyType getType(){return type;} public void setType(DependencyType v){type=v;}
    public Integer getLag(){return lag;} public void setLag(Integer v){lag=v;}
}
