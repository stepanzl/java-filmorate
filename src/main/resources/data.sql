MERGE INTO genres (genre_id, name) KEY (genre_id) VALUES (1, 'Комедия');
MERGE INTO genres (genre_id, name) KEY (genre_id) VALUES (2, 'Драма');
MERGE INTO genres (genre_id, name) KEY (genre_id) VALUES (3, 'Мультфильм');
MERGE INTO genres (genre_id, name) KEY (genre_id) VALUES (4, 'Триллер');
MERGE INTO genres (genre_id, name) KEY (genre_id) VALUES (5, 'Документальный');
MERGE INTO genres (genre_id, name) KEY (genre_id) VALUES (6, 'Боевик');

MERGE INTO mpa_ratings (mpa_rating_id, name) KEY (mpa_rating_id) VALUES (1, 'G');
MERGE INTO mpa_ratings (mpa_rating_id, name) KEY (mpa_rating_id) VALUES (2, 'PG');
MERGE INTO mpa_ratings (mpa_rating_id, name) KEY (mpa_rating_id) VALUES (3, 'PG-13');
MERGE INTO mpa_ratings (mpa_rating_id, name) KEY (mpa_rating_id) VALUES (4, 'R');
MERGE INTO mpa_ratings (mpa_rating_id, name) KEY (mpa_rating_id) VALUES (5, 'NC-17');