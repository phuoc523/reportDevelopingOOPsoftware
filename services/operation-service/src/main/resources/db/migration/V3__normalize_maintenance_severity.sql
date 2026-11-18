UPDATE maintenance_tickets
SET severity = CASE severity
    WHEN 'LOW' THEN 'MINOR'
    WHEN 'MEDIUM' THEN 'MODERATE'
    WHEN 'HIGH' THEN 'MAJOR'
    ELSE severity
END
WHERE severity IN ('LOW', 'MEDIUM', 'HIGH');
