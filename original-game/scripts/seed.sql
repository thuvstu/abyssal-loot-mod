TRUNCATE TABLE runs, artifacts, heroes CASCADE;

INSERT INTO heroes (name, class_id, souls, best_floor, best_score, deepest_kills, total_runs, total_kills, victories) VALUES
  ('トリガー', 'gunner',   3120, 25, 96340, 612, 12, 1876, 1),
  ('ミリア',   'mage',     2010, 22, 58940, 498,  9, 1288, 0),
  ('クレハ',   'ninja',    1240, 18, 42180, 402,  7,  942, 0),
  ('バルド',   'paladin',   880, 15, 31260, 356,  5,  705, 0),
  ('ゴウセン', 'samurai',   640, 12, 22410, 268,  4,  512, 0),
  ('ユウギリ', 'samurai',   420,  9, 15880, 188,  3,  344, 0);

INSERT INTO runs (hero_id, hero_name, class_id, floor, level, kills, gold, score, duration_sec, victory, weapon_name, rarity, build)
SELECT h.id, v.hero_name, v.class_id, v.floor, v.level, v.kills, v.gold, v.score, v.dur, v.vic, v.wn, v.rar, v.build::jsonb
FROM (VALUES
  ('トリガー','gunner',  25,41,612,3120,96340,1842,true,'虚無神の残光','mythic',   '["分裂の刻印","連鎖雷撃","処刑人","追尾魔弾","貫通の刻印"]'),
  ('ミリア',  'mage',    22,36,498,2210,58940,1533,false,'星辰のアークスタッフ','legendary','["拡大の刻印","氷霜新星","回転刃","吸血の刻印"]'),
  ('クレハ',  'ninja',   18,30,402,1680,42180,1204,false,'天羽々斬','legendary',   '["迅風の刻印","心眼の刻印","回転刃","大地の怒り"]'),
  ('バルド',  'paladin', 15,26,356,1240,31260,1088,false,'デュランダル','legendary','["鉄壁の刻印","聖なる領域","巨躯の刻印","血の契約"]'),
  ('ゴウセン','samurai', 12,21,268, 860,22410, 812,false,'修羅のバトルアクス','rare','["剛力の刻印","拡大の刻印","爆砕の刻印"]'),
  ('ミリア',  'mage',    11,19,233, 720,18420, 745,false,'雷光のクロスボウ','rare', '["知識の渇望","連鎖雷撃"]'),
  ('ユウギリ','samurai',  9,16,188, 540,15880, 631,false,'深淵の呪骨の杖','rare',   '["吸血の刻印","致命の刻印"]'),
  ('クレハ',  'ninja',    8,15,164, 480,13210, 566,false,'月影のツインダガー','rare','["迅風の刻印","貫通の刻印"]')
) AS v(hero_name, class_id, floor, level, kills, gold, score, dur, vic, wn, rar, build)
JOIN heroes h ON h.name = v.hero_name;

INSERT INTO artifacts (hero_name, item_name, slot, rarity, power, floor, affixes) VALUES
  ('トリガー','虚無神の残光','weapon','mythic',1482,24,'["攻撃力 +214","攻撃速度 +38%","追加弾数 +1","クリティカル威力 +96%","爆裂確率 +21%"]'),
  ('ミリア','無限の歯車','ring','mythic',1204,21,'["連鎖雷撃 +2","攻撃力 +42%","クリティカル率 +11.4%","魂ゲージ増加 +38%"]'),
  ('トリガー','神々の黄昏','armor','mythic',1096,23,'["最大HP +412","ダメージ軽減 +26%","HP自動回復 +3.2/秒","棘ダメージ +58"]'),
  ('ミリア','運命を断つ者','amulet','mythic',1042,22,'["攻撃力 +188%","追加弾数 +1","生命吸収 +7.2%","運 +64"]'),
  ('クレハ','天羽々斬','weapon','legendary',918,17,'["攻撃力 +126","攻撃速度 +31%","貫通 +1","会心 +9.8%"]'),
  ('バルド','デュランダル','weapon','legendary',842,15,'["攻撃力 +154","攻撃範囲 +38%","棘ダメージ +42","ダメージ軽減 +14%"]'),
  ('ミリア','原初の炎','amulet','legendary',766,19,'["攻撃力 +92%","爆裂確率 +24%","氷結確率 +28%","経験値 +34%"]'),
  ('ゴウセン','竜王のプレートアーマー','armor','rare',412,12,'["最大HP +188","ダメージ軽減 +19%","移動速度 -6%"]');
