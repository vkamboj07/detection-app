CREATE OR REPLACE FUNCTION get_unique_devices_count(since TIMESTAMPTZ)
RETURNS INTEGER
LANGUAGE SQL
STABLE
AS $$
  SELECT COUNT(DISTINCT device_id)::INTEGER
  FROM observations
  WHERE timestamp >= since;
$$;
