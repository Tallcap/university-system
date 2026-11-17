USE UniversitySystem;
GO

DECLARE @constraint_name sysname;

SELECT TOP (1) @constraint_name = check_constraint.name
FROM sys.check_constraints AS check_constraint
INNER JOIN sys.columns AS column_info
    ON column_info.object_id = check_constraint.parent_object_id
   AND column_info.column_id = check_constraint.parent_column_id
WHERE check_constraint.parent_object_id = OBJECT_ID(N'dbo.evaluation')
  AND column_info.name = N'evaluation_type';

IF @constraint_name IS NOT NULL
BEGIN
    EXEC(N'ALTER TABLE dbo.evaluation DROP CONSTRAINT ' + QUOTENAME(@constraint_name));
END;

ALTER TABLE dbo.evaluation WITH CHECK
ADD CONSTRAINT CK_evaluation_evaluation_type
CHECK (evaluation_type IN (
    'PC_1',
    'PC_2',
    'ACADEMIC_TASK',
    'PARCIAL_EXAM',
    'CLASS_PARTICIPATION',
    'PORTFOLIO_PROJECT_ADVANCE',
    'FINAL_PORTFOLIO_PROJECT',
    'OTHER',
    'FINAL_EXAM'
));
GO
