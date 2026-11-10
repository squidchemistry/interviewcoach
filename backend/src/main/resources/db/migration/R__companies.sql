-- Reference data: companies candidates can target. Repeatable migration — Flyway re-runs it whenever this file
-- changes; the upsert keeps it idempotent and never deletes companies that users may already reference.
INSERT INTO companies (name, slug) VALUES
    ('Adobe', 'adobe'),
    ('Amazon', 'amazon'),
    ('Atlassian', 'atlassian'),
    ('Flipkart', 'flipkart'),
    ('Goldman Sachs', 'goldman-sachs'),
    ('Google', 'google'),
    ('Meesho', 'meesho'),
    ('Meta', 'meta'),
    ('Microsoft', 'microsoft'),
    ('Oracle', 'oracle'),
    ('PhonePe', 'phonepe'),
    ('Razorpay', 'razorpay'),
    ('Salesforce', 'salesforce'),
    ('Swiggy', 'swiggy'),
    ('Uber', 'uber'),
    ('Walmart Global Tech', 'walmart-global-tech'),
    ('Zomato', 'zomato')
ON CONFLICT (slug) DO UPDATE SET name = EXCLUDED.name;
