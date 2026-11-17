SELECT actslab.company_sample_field_value(:master_acts_no, 'NATIONAL_BD_NM') AS national_brand_name FROM dual;

SELECT m.master_acts_no, m.company_id, m.sample_type_id, m.acts_location_id, m.pre_test_program_id, m.ms_received_date, f.non_tpd_pretest_flag, f.is_zdhc_mass_market
FROM actslab.master_sample m
LEFT JOIN actslab.master_sample_add_flag f ON f.master_acts_no = m.master_acts_no
WHERE m.master_acts_no = :master_acts_no;

SELECT d.master_acts_no, f.sample_field_id, f.company_id, f.sample_type_id, f.acts_location_id, f.sf_code, f.sf_inactive, d.sfd_data
FROM actslab.company_sample_field_data d
JOIN actslab.company_sample_field f ON f.sample_field_id = d.sample_field_id
WHERE d.master_acts_no = :master_acts_no
  AND f.sf_code = 'NATIONAL_BD_NM'
ORDER BY f.company_id DESC, f.acts_location_id, f.sample_field_id;

SELECT f.sample_field_id, f.company_id, f.sample_type_id, f.acts_location_id, f.sf_code, f.sf_inactive, d.sfd_data
FROM actslab.company_sample_field f
LEFT JOIN actslab.company_sample_field_data d ON d.sample_field_id = f.sample_field_id AND d.master_acts_no = :master_acts_no
WHERE f.company_id IN (:effective_company_id, 0)
  AND f.sample_type_id = :sample_type_id
  AND f.acts_location_id IN (:location_id, 0)
  AND f.sf_inactive = 0
  AND f.sf_code = 'NATIONAL_BD_NM'
ORDER BY f.company_id DESC, f.acts_location_id, f.sample_field_id;

SELECT company_id, sample_type_id, location_id, property_id, property_desc
FROM actslab.misc_setup
WHERE company_id IN (2083114, 0)
  AND property_id IN (3373, 3393)
ORDER BY property_id, company_id DESC;

SELECT company_id, sample_type_id, location_id, report_type_id, language_id, bookmark_name, bookmark_value, order_no, section
FROM actslab.new_arg_report_header
WHERE company_id = :header_company_id
  AND sample_type_id = :sample_type_id
  AND location_id = :location_id
  AND report_type_id = :report_type_id
  AND language_id = :language_id
ORDER BY order_no;

SELECT owner, object_name, object_type, status
FROM all_objects
WHERE object_name = 'COMPANY_SAMPLE_FIELD_VALUE'
  AND object_type = 'FUNCTION';

SELECT owner, synonym_name, table_owner, table_name, db_link
FROM all_synonyms
WHERE synonym_name = 'COMPANY_SAMPLE_FIELD_VALUE';
