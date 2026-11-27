-- I1. Phim thể loại ACTION (xem đề). Viết MỘT câu SELECT bên dưới, kết thúc bằng dấu ;
select m.title,
       m.release_date
from movies m
where m.genre = 'ACTION'
order by m.release_date DESC,
         m.title ASC;
