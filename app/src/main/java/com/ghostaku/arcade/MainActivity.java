package com.ghostaku.arcade;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.*;
import android.os.Bundle;
import android.view.*;
import java.util.*;

public class MainActivity extends Activity {
  private Arcade view;

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    setVolumeControlStream(android.media.AudioManager.STREAM_MUSIC);
    getWindow().setStatusBarColor(0xff101425);
    getWindow().setNavigationBarColor(0xff101425);
    if (android.os.Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
    else
      getWindow()
          .getDecorView()
          .setSystemUiVisibility(
              View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                  | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                  | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    view = new Arcade(this);
    setContentView(view);
  }

  @Override
  protected void onPause() {
    super.onPause();
    view.active = false;
    view.pause();
    view.save();
    view.feedback.stop();
  }

  @Override
  protected void onResume() {
    super.onResume();
    view.active = true;
    view.last = 0;
    view.invalidate();
  }

  @Override
  protected void onDestroy() {
    view.feedback.release();
    super.onDestroy();
  }

  @Override
  public void onBackPressed() {
    if (view.setup != 0) {
      view.setup = 0;
      view.invalidate();
    } else if (view.game != 0) view.askHome();
    else super.onBackPressed();
  }

  final class Arcade extends View {
    final int bg = 0xff101425,
        panel = 0xff1c2237,
        deep = 0xff0b101e,
        edge = 0xff373e5b,
        ink = 0xfffff2d4,
        muted = 0xffaebad0,
        lime = 0xffddf678;
    final int[] colors = {
      0, 0xff71dfec, 0xffffcf6f, 0xffc2a6fa, 0xffc9ec6c, 0xfffb91aa, 0xff8bafff, 0xffffa474
    };
    final String[] names = {"", "俄罗斯方块", "宝石三消", "乒乓对决", "扫雷", "贪吃蛇", "打砖块"};
    final String[] en = {"", "BLOCKS", "MATCH", "PONG", "MINES", "SNAKE", "BREAKER"};
    final String[] subs = {
      "", "经典 / 解压", "限步 / 限时 / 无限", "人机对打 / 侧旋", "经典 / 自定义", "滑动转向", "三条命 / 清全场"
    };
    final Typeface bold = Typeface.create("sans-serif", Typeface.BOLD);
    final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    Canvas c;
    float scale, offsetX, offsetY;
    int game, setup, sx = -1, sy = -1, mode, cols = 9, rows = 9, mineCount = 10;
    boolean paused, active = true, flagMode, resultSaved, powers = true, bombArmed, sound, haptic;
    int[] hint;
    float downX, downY, fall, elapsed, flash, messageTime;
    long last;
    String message = "";
    final ArrayList<Hit> hits = new ArrayList<>();
    Games.Blocks blocks;
    Games.Match match;
    Games.Mines mines;
    Games.Pong pong;
    Games.Snake snake;
    Games.Breaker breaker;
    final SharedPreferences prefs;
    final Feedback feedback;

    class Hit {
      final float x, y, w, h;
      final Runnable run;

      Hit(float x, float y, float w, float h, Runnable run) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.run = run;
      }

      boolean contains(float a, float b) {
        return a >= x && a < x + w && b >= y && b < y + h;
      }
    }

    Arcade(Context context) {
      super(context);
      prefs = context.getSharedPreferences("records", 0);
      sound = prefs.getBoolean("sound", true);
      haptic = prefs.getBoolean("haptic", true);
      cols = prefs.getInt("cols", 9);
      rows = prefs.getInt("rows", 9);
      mineCount = prefs.getInt("mines", 10);
      powers = prefs.getBoolean("powers", true);
      feedback = new Feedback(context);
      setFocusable(true);
      setContentDescription("口袋街机，六款离线游戏");
      setOnApplyWindowInsetsListener(
          (v, insets) -> {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
              android.graphics.Insets i =
                  insets.getInsets(
                      WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
              setPadding(i.left, i.top, i.right, i.bottom);
            } else
              setPadding(
                  insets.getSystemWindowInsetLeft(),
                  insets.getSystemWindowInsetTop(),
                  insets.getSystemWindowInsetRight(),
                  insets.getSystemWindowInsetBottom());
            return insets;
          });
    }

    void fx(int type) {
      if (active) feedback.play(this, type, sound, haptic);
    }

    void pause() {
      if (game != 0 && !over()) paused = true;
    }

    void askHome() {
      if (over()) {
        leave();
        return;
      }
      boolean wasPaused = paused;
      pause();
      new AlertDialog.Builder(MainActivity.this)
          .setTitle("返回大厅？")
          .setMessage("本局会结束，已获得的纪录会保留。")
          .setPositiveButton("返回大厅", (d, w) -> leave())
          .setNegativeButton("留在这里", (d, w) -> resume(wasPaused))
          .setOnCancelListener(d -> resume(wasPaused))
          .show();
      invalidate();
    }

    void resume(boolean isPaused) {
      paused = isPaused;
      last = 0;
      invalidate();
    }

    void leave() {
      save();
      game = 0;
      setup = 0;
      paused = false;
      invalidate();
    }

    void restart() {
      boolean wasPaused = paused;
      pause();
      new AlertDialog.Builder(MainActivity.this)
          .setTitle("从头来一局？")
          .setNegativeButton("取消", (d, w) -> resume(wasPaused))
          .setPositiveButton(
              "重开",
              (d, w) -> {
                save();
                start(game);
              })
          .setOnCancelListener(d -> resume(wasPaused))
          .show();
      invalidate();
    }

    void select(int g) {
      fx(Feedback.SELECT);
      mode = 0;
      if (g == 1 || g == 2 || g == 4) {
        setup = g;
        invalidate();
      } else start(g);
    }

    void start(int g) {
      game = g;
      setup = 0;
      paused = resultSaved = flagMode = bombArmed = false;
      sx = sy = -1;
      hint = null;
      fall = elapsed = flash = messageTime = 0;
      last = 0;
      blocks = g == 1 ? new Games.Blocks(mode == 1) : null;
      match = g == 2 ? new Games.Match(mode, powers) : null;
      pong = g == 3 ? new Games.Pong() : null;
      mines =
          g == 4 ? (mode == 0 ? new Games.Mines() : new Games.Mines(cols, rows, mineCount)) : null;
      snake = g == 5 ? new Games.Snake() : null;
      snakeReady = g == 5;
      breaker = g == 6 ? new Games.Breaker() : null;
      prefs
          .edit()
          .putInt("cols", cols)
          .putInt("rows", rows)
          .putInt("mines", mineCount)
          .putBoolean("powers", powers)
          .apply();
      invalidate();
    }

    boolean over() {
      switch (game) {
        case 1:
          return blocks.over;
        case 2:
          return match.over;
        case 3:
          return pong.over;
        case 4:
          return mines.over;
        case 5:
          return snake.over;
        case 6:
          return breaker.over;
        default:
          return false;
      }
    }

    boolean won() {
      return game == 4
          ? mines.won
          : game == 3
              ? pong.score == 7
              : game == 6 ? breaker.won : game == 5 ? snake.won : game == 2;
    }

    int score() {
      switch (game) {
        case 1:
          return blocks.score;
        case 2:
          return match.score;
        case 3:
          return pong.score;
        case 4:
          return mines.revealed;
        case 5:
          return snake.score;
        case 6:
          return breaker.score;
        default:
          return 0;
      }
    }

    String key() {
      return "best" + game + (game == 2 ? "_" + mode + (powers ? "_tools" : "_plain") : "");
    }

    void save() {
      if (game == 0 || (game == 1 && mode == 1)) return;
      if (game == 4) {
        if (mines.won) {
          String k = mode == 0 ? "minesTime" : "mines_" + cols + "_" + rows + "_" + mineCount;
          int t = Math.max(1, (int) elapsed);
          if (t < prefs.getInt(k, Integer.MAX_VALUE)) prefs.edit().putInt(k, t).apply();
        }
        return;
      }
      int n = score();
      if (n > prefs.getInt(key(), 0)) prefs.edit().putInt(key(), n).apply();
    }

    void text(String s, float x, float y, float size, int color) {
      p.setColor(color);
      p.setTextSize(size);
      p.setTypeface(bold);
      c.drawText(s, x, y, p);
    }

    void mono(String s, float x, float y, float size, int color) {
      p.setColor(color);
      p.setTextSize(size);
      p.setTypeface(Typeface.MONOSPACE);
      c.drawText(s, x, y, p);
    }

    void rect(float x, float y, float w, float h, int color) {
      p.setColor(color);
      c.drawRect(x, y, x + w, y + h, p);
    }

    void outline(float x, float y, float w, float h, int color) {
      p.setColor(color);
      p.setStyle(Paint.Style.STROKE);
      p.setStrokeWidth(1.5f);
      c.drawRect(x, y, x + w, y + h, p);
      p.setStyle(Paint.Style.FILL);
    }

    void dotField(float x, float y, float w, float h, int step, int color) {
      for (float a = x; a < x + w; a += step)
        for (float b = y; b < y + h; b += step) rect(a, b, 2, 2, color);
    }

    void frame(float x, float y, float w, float h, int accent) {
      rect(x, y + 4, w, h, deep);
      rect(x, y, w, h, panel);
      outline(x, y, w, h, edge);
      rect(x, y, 12, 2, accent);
      rect(x, y, 2, 12, accent);
      rect(x + w - 12, y + h - 2, 12, 2, accent);
      rect(x + w - 2, y + h - 12, 2, 12, accent);
    }

    void button(String title, float x, float y, float w, float h, int color, Runnable action) {
      rect(x + 2, y + 4, w, h, deep);
      rect(x, y, w, h, color);
      rect(x + 2, y + 2, w - 4, 2, color == lime ? 0x55ffffff : 0xff424a67);
      rect(x + 2, y + h - 3, w - 4, 2, 0x66000000);
      p.setTextSize(15);
      p.setTypeface(bold);
      text(title, x + (w - p.measureText(title)) / 2, y + h / 2 + 5, 15, color == lime ? bg : ink);
      if (action != null) hits.add(new Hit(x, y, w, h, action));
    }

    void tile(float x, float y, float size, int col) {
      rect(x + 1, y + 1, size - 2, size - 2, col);
      if (size < 8) return;
      rect(x + 2, y + 2, size - 4, 2, 0x55ffffff);
      rect(x + 2, y + 2, 2, size - 4, 0x33ffffff);
      rect(x + 3, y + size - 4, size - 5, 2, 0x55000000);
      rect(x + size - 4, y + 4, 2, size - 6, 0x33000000);
    }

    void stamp(String pattern, float x, float y, int size, int color) {
      String[] lines = pattern.split("/");
      for (int row = 0; row < lines.length; row++)
        for (int col = 0; col < lines[row].length(); col++)
          if (lines[row].charAt(col) == '#')
            rect(x + col * size, y + row * size, size, size, color);
    }

    void pixel(String s, float x, float y, int size, int color) {
      for (char ch : s.toCharArray()) {
        String glyph;
        switch (ch) {
          case 'P':
            glyph = "####./#...#/#...#/####./#..../#..../#....";
            break;
          case 'O':
            glyph = ".###./#...#/#...#/#...#/#...#/#...#/.###.";
            break;
          case 'C':
            glyph = ".####/#..../#..../#..../#..../#..../.####";
            break;
          case 'K':
            glyph = "#...#/#..#./#.#../##.../#.#../#..#./#...#";
            break;
          case 'E':
            glyph = "#####/#..../#..../####./#..../#..../#####";
            break;
          case 'T':
            glyph = "#####/..#../..#../..#../..#../..#../..#..";
            break;
          case 'A':
            glyph = ".###./#...#/#...#/#####/#...#/#...#/#...#";
            break;
          case 'R':
            glyph = "####./#...#/#...#/####./#.#../#..#./#...#";
            break;
          case 'D':
            glyph = "####./#...#/#...#/#...#/#...#/#...#/####.";
            break;
          default:
            glyph = "";
            break;
        }
        stamp(glyph, x, y, size, color);
        x += size * 6;
      }
    }

    @Override
    protected void onDraw(Canvas canvas) {
      super.onDraw(canvas);
      c = canvas;
      c.drawColor(bg);
      float w = getWidth() - getPaddingLeft() - getPaddingRight(),
          h = getHeight() - getPaddingTop() - getPaddingBottom();
      scale = Math.min(w / 400f, h / 800f);
      if (scale <= 0) return;
      offsetX = getPaddingLeft() + (w - 400 * scale) / 2;
      offsetY = getPaddingTop() + (h - 800 * scale) / 2;
      c.save();
      c.translate(offsetX, offsetY);
      c.scale(scale, scale);
      hits.clear();
      if (game != 0 && active && !paused && !over()) advance();
      if (game == 0) {
        if (setup == 0) home();
        else config();
      } else {
        header();
        switch (game) {
          case 1:
            drawBlocks();
            break;
          case 2:
            drawMatch();
            break;
          case 3:
            drawPong();
            break;
          case 4:
            drawMines();
            break;
          case 5:
            drawSnake();
            break;
          case 6:
            drawBreaker();
            break;
          default:
            break;
        }
        if (over()) {
          if (!resultSaved) {
            save();
            resultSaved = true;
            fx(won() ? Feedback.WIN : Feedback.FAIL);
          }
          overlay(true);
        } else if (paused) overlay(false);
      }
      c.restore();
      if (active
          && game != 0
          && !paused
          && !over()
          && (game != 1 || !blocks.free || messageTime > 0 || flash > 0)
          && (game != 5 || !snakeReady)) postInvalidateOnAnimation();
    }

    void advance() {
      long now = System.nanoTime();
      float dt = last == 0 ? 0 : (now - last) / 1e9f;
      last = now;
      float step = Math.min(.05f, dt);
      if (game != 4 || mines.placed) elapsed += dt;
      if (game == 1 && !blocks.free) {
        fall += step;
        if (fall >= Math.max(.09f, .68f - blocks.lines / 10 * .055f)) {
          fall = 0;
          int lines = blocks.lines, score = blocks.score;
          int row = blocks.y;
          blocks.tick();
          if (!blocks.over && (blocks.y <= row || blocks.score != score)) blockCue(lines);
        }
      }
      if (game == 2) match.tick(dt);
      if (game == 3) {
        int b = pong.bounces, s = pong.score + pong.enemy;
        pong.tick(step);
        if (!pong.over) {
          if (s != pong.score + pong.enemy) fx(Feedback.CLEAR);
          else if (b != pong.bounces) fx(Feedback.BOUNCE);
        }
      }
      if (game == 5 && !snakeReady) {
        fall += step;
        if (fall >= snake.interval()) {
          fall = 0;
          int s = snake.score;
          snake.tick();
          if (!snake.over && s != snake.score) fx(Feedback.EAT);
        }
      }
      if (game == 6) {
        int b = breaker.hits, s = breaker.score, l = breaker.lives;
        breaker.tick(step);
        if (!breaker.over) {
          if (l != breaker.lives) fx(Feedback.FAIL);
          else if (s != breaker.score) fx(Feedback.DROP);
          else if (b != breaker.hits) fx(Feedback.BOUNCE);
        }
      }
      messageTime -= step;
      flash -= step;
    }

    void home() {
      dotField(15, 15, 370, 770, 8, 0xff20263b);
      frame(16, 20, 368, 183, colors[1]);
      rect(26, 30, 348, 163, deep);
      dotField(34, 41, 332, 138, 7, 0xff1a3244);
      mono("01P  /  SELECT GAME", 37, 54, 12, colors[1]);
      mono("●  OFFLINE", 280, 54, 11, lime);
      pixel("POCKET", 40, 72, 6, 0xff357483);
      pixel("POCKET", 36, 68, 6, colors[1]);
      pixel("ARCADE", 40, 121, 6, 0xff956a3a);
      pixel("ARCADE", 36, 117, 6, colors[2]);
      stamp(".#####./#######/##.#.##/#######/.#...#./#.....#", 299, 85, 8, lime);
      text("口袋街机", 36, 183, 16, ink);
      mono("VOL.02   /   6-IN-1", 222, 182, 11, muted);
      for (int i = 1; i <= 6; i++) {
        final int g = i;
        float x = (i % 2 == 1 ? 16 : 205), y = 220 + (i - 1) / 2 * 145;
        int a = colors[i];
        frame(x, y, 179, 132, a);
        rect(x + 8, y + 8, 163, 58, deep);
        dotField(x + 13, y + 13, 150, 48, 6, 0xff262e44);
        mono("0" + i, x + 14, y + 26, 11, a);
        mono(en[i], x + 14, y + 48, 10, muted);
        art(i, x + 99, y + 16, a);
        text(names[i], x + 12, y + 94, 20, ink);
        text(subs[i], x + 12, y + 116, 12, muted);
        stamp("#../##./###/##./#..", x + 151, y + 87, 3, a);
        hits.add(new Hit(x, y, 179, 132, () -> select(g)));
      }
      button(
          sound ? "音效：开" : "音效：关",
          16,
          662,
          178,
          48,
          panel,
          () -> {
            sound = !sound;
            prefs.edit().putBoolean("sound", sound).apply();
            fx(Feedback.SELECT);
          });
      button(
          haptic ? "触感：开" : "触感：关",
          205,
          662,
          179,
          48,
          panel,
          () -> {
            haptic = !haptic;
            prefs.edit().putBoolean("haptic", haptic).apply();
            fx(Feedback.SELECT);
          });
      rect(20, 737, 360, 2, edge);
      mono("NO COINS NEEDED   [ FREE PLAY ]", 34, 766, 14, lime);
    }

    void art(int i, float x, float y, int a) {
      if (i == 1) {
        for (int[] q : Games.Blocks.SHAPES[2]) tile(x + q[0] * 16, y + q[1] * 16, 16, a);
        return;
      }
      if (i == 2) {
        stamp("..#..#./.######/#######/.#####./..###../...#...", x, y, 6, a);
        return;
      }
      if (i == 3) {
        rect(x, y, 6, 32, a);
        rect(x + 44, y + 12, 6, 32, a);
        tile(x + 20, y + 13, 11, ink);
        return;
      }
      if (i == 4) {
        stamp("...#.../.#####./#######/###.###/#######/.#####./...#...", x, y, 6, a);
        return;
      }
      if (i == 5) {
        stamp("..#####/..##.##/..#..../###..../###....", x, y, 7, a);
        tile(x + 43, y + 30, 10, colors[2]);
        return;
      }
      for (int j = 0; j < 3; j++) rect(x + j * 17, y, 14, 7, colors[j + 1]);
      tile(x + 22, y + 15, 9, ink);
      rect(x + 10, y + 35, 35, 5, a);
    }

    void config() {
      int a = colors[setup];
      dotField(16, 16, 368, 768, 9, 0xff20263b);
      button(
          "‹ 大厅",
          20,
          20,
          96,
          46,
          panel,
          () -> {
            setup = 0;
            fx(Feedback.SELECT);
          });
      mono("SETUP / 0" + setup, 250, 49, 13, a);
      text(names[setup], 24, 116, 31, ink);
      mono(en[setup] + "  /  MODE SELECT", 25, 145, 12, a);
      if (setup == 1) {
        modeCard(0, 181, "经典模式", "自动下落 · 七袋随机 · 每十行加速");
        modeCard(1, 274, "解压模式", "随选 7 种方块，手动落下，支持撤销");
        text("解压模式不计入经典纪录。", 26, 407, 15, muted);
      } else if (setup == 2) {
        modeCard(0, 181, "限步模式", "30 次有效交换，连击能叠加分数");
        modeCard(1, 274, "限时模式", "90 秒内尽量多消除，交换不限");
        modeCard(2, 367, "无限模式", "没有步数与时间限制");
        button(
            powers ? "道具：开" : "道具：关",
            24,
            477,
            160,
            46,
            powers ? lime : panel,
            () -> {
              powers = !powers;
              fx(Feedback.SELECT);
            });
        text("提示 ×3  /  炸弹 ×2  /  洗牌 ×2", 25, 555, 14, muted);
        text("道具不消耗步数，也不会暂停计时。", 25, 581, 13, muted);
      } else {
        modeCard(0, 181, "经典雷区", "9 × 9　/　10 颗地雷");
        modeCard(1, 274, "自定义雷区", "调整宽、高和地雷数");
        if (mode == 1) {
          dial(
              "宽度",
              cols,
              391,
              () -> {
                cols = Math.max(6, cols - 1);
                limitMines();
              },
              () -> {
                cols = Math.min(16, cols + 1);
                limitMines();
              });
          dial(
              "高度",
              rows,
              452,
              () -> {
                rows = Math.max(6, rows - 1);
                limitMines();
              },
              () -> {
                rows = Math.min(16, rows + 1);
                limitMines();
              });
          dial("地雷", mineCount, 513, () -> limitMineChange(-1), () -> limitMineChange(1));
          button("−10", 193, 579, 81, 45, panel, () -> limitMineChange(-10));
          button("+10", 284, 579, 81, 45, panel, () -> limitMineChange(10));
          text("地雷上限 " + (cols * rows - 9) + "，首点周围保留安全区。", 24, 656, 13, muted);
        } else text("点击已翻开的数字，可按旗数快速挖开邻格。", 24, 407, 14, muted);
      }
      button(
          "开始游戏  ▶",
          24,
          702,
          352,
          61,
          lime,
          () -> {
            fx(Feedback.SELECT);
            start(setup);
          });
    }

    void modeCard(int id, int y, String name, String details) {
      int a = mode == id ? colors[setup] : edge;
      frame(24, y, 352, 77, a);
      if (mode == id) rect(25, y + 1, 5, 75, a);
      text(name, 44, y + 32, 20, ink);
      text(details, 44, y + 57, 13, muted);
      outline(331, y + 23, 18, 18, a);
      if (mode == id) rect(335, y + 27, 10, 10, a);
      hits.add(
          new Hit(
              24,
              y,
              352,
              77,
              () -> {
                mode = id;
                fx(Feedback.SELECT);
              }));
    }

    void dial(String label, int value, int y, Runnable minus, Runnable plus) {
      text(label, 24, y + 31, 16, ink);
      button(
          "−",
          140,
          y,
          57,
          46,
          panel,
          () -> {
            minus.run();
            fx(Feedback.SELECT);
          });
      mono("" + value, 219, y + 30, 20, lime);
      button(
          "+",
          310,
          y,
          57,
          46,
          panel,
          () -> {
            plus.run();
            fx(Feedback.SELECT);
          });
    }

    void limitMineChange(int n) {
      mineCount += n;
      limitMines();
      fx(Feedback.SELECT);
    }

    void limitMines() {
      mineCount = Math.max(1, Math.min(cols * rows - 9, mineCount));
    }

    void header() {
      button("‹ 大厅", 20, 17, 82, 42, panel, () -> askHome());
      mono("P1  /  " + en[game], 218, 42, 12, colors[game]);
      text(names[game], 24, 101, 27, ink);
      button(
          "暂停",
          224,
          68,
          72,
          42,
          panel,
          () -> {
            fx(Feedback.SELECT);
            pause();
          });
      button("重开", 307, 68, 72, 42, panel, () -> restart());
      rect(20, 120, 360, 2, edge);
    }

    void stat(String key, String value, int x) {
      mono(key, x, 143, 10, muted);
      mono(value, x, 169, 23, colors[game]);
    }

    void footer(String s) {
      rect(20, 750, 360, 2, edge);
      text(s, 24, 775, 12, muted);
    }

    void toast(String s) {
      message = s;
      messageTime = 2.2f;
    }

    void blockCue(int before) {
      if (blocks.lines != before) {
        flash = .16f;
        fx(Feedback.CLEAR);
      } else fx(Feedback.DROP);
    }

    void drawBlocks() {
      stat("SCORE", "" + blocks.score, 24);
      stat("LINES", "" + blocks.lines, 162);
      stat("MODE", blocks.free ? "FREE" : "LV " + (1 + blocks.lines / 10), 283);
      int ox = 22, oy = 190, s = 21;
      frame(ox - 2, oy - 2, 214, 424, colors[1]);
      for (int y = 0; y < 20; y++)
        for (int x = 0; x < 10; x++) {
          rect(ox + x * s, oy + y * s, s - 1, s - 1, 0xff111b2b);
          rect(ox + x * s + 9, oy + y * s + 9, 2, 2, 0xff273148);
          if (blocks.board[y][x] > 0) tile(ox + x * s, oy + y * s, s, colors[blocks.board[y][x]]);
        }
      if (!blocks.over && !blocks.blocked) {
        int ghost = blocks.ghost();
        for (int[] a : blocks.piece)
          outline(
              ox + (blocks.x + a[0]) * s + 3,
              oy + (ghost + a[1]) * s + 3,
              s - 6,
              s - 6,
              0xff64869b);
        for (int[] a : blocks.piece)
          tile(ox + (blocks.x + a[0]) * s, oy + (blocks.y + a[1]) * s, s, colors[blocks.type + 1]);
      }
      if (flash > 0) outline(ox, oy, 210, 420, lime);
      mono(blocks.free ? "PIECE" : "NEXT", 256, 213, 13, muted);
      int next = blocks.free ? blocks.type : blocks.next;
      for (int[] a : Games.Blocks.SHAPES[next])
        tile(255 + a[0] * 22, 230 + a[1] * 22, 22, colors[next + 1]);
      button(
          "旋转 ↻",
          249,
          306,
          131,
          54,
          panel,
          () -> {
            if (blocks.rotate()) fx(Feedback.SELECT);
          });
      button(
          "落下 ↓↓",
          249,
          371,
          131,
          54,
          lime,
          () -> {
            int before = blocks.lines;
            if (blocks.drop()) {
              fall = 0;
              blockCue(before);
            } else toast("换个方块，或撤销上一次放置。");
          });
      if (blocks.free) {
        button(
            "撤销放置",
            249,
            441,
            131,
            49,
            panel,
            () -> {
              if (blocks.undo()) fx(Feedback.POWER);
              else toast("还没有可撤销的放置。");
            });
        button(
            "清空棋盘",
            249,
            502,
            131,
            49,
            panel,
            () ->
                new AlertDialog.Builder(MainActivity.this)
                    .setTitle("清空解压棋盘？")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("清空", (d, w) -> start(1))
                    .show());
        if (blocks.blocked) text("顶部被挡住了", 247, 579, 14, colors[5]);
      } else {
        text("经典最高 " + prefs.getInt(key(), 0), 254, 481, 14, muted);
        text("空心框是落点", 254, 511, 13, muted);
      }
      button(
          "◀",
          22,
          626,
          104,
          48,
          panel,
          () -> {
            if (blocks.move(-1, 0)) fx(Feedback.SELECT);
          });
      button(
          "↓",
          142,
          626,
          104,
          48,
          panel,
          () -> {
            if (blocks.move(0, 1)) {
              if (!blocks.free) blocks.score++;
              fx(Feedback.SELECT);
            }
          });
      button(
          "▶",
          262,
          626,
          117,
          48,
          panel,
          () -> {
            if (blocks.move(1, 0)) fx(Feedback.SELECT);
          });
      if (blocks.free)
        for (int i = 0; i < 7; i++) {
          final int t = i;
          float x = 20 + i * 52;
          rect(x, 687, 47, 53, blocks.type == i ? edge : panel);
          if (blocks.type == i) outline(x, 687, 47, 53, lime);
          for (int[] a : Games.Blocks.SHAPES[i])
            tile(x + 5 + a[0] * 9, 697 + a[1] * 9, 9, colors[i + 1]);
          mono("IOTSZJL".substring(i, i + 1), x + 19, 733, 10, ink);
          hits.add(
              new Hit(
                  x,
                  687,
                  47,
                  53,
                  () -> {
                    blocks.choose(t);
                    fx(Feedback.SELECT);
                  }));
        }
      footer(
          messageTime > 0
              ? message
              : blocks.free ? "选方块 → 排位置 → 落下；没有自动下落。" : "每消除 10 行提速；直落会立即锁定。");
    }

    final String[] jewels = {
      "..###../.#####./#######/#######/#######/.#####./..###..",
      "#######/#######/##...##/##...##/##...##/#######/#######",
      "...#.../..###../..###../.#####./.#####./#######/#######",
      "...#.../..###../.#####./#######/.#####./..###../...#...",
      "...#.../#..#..#/.#####./..###../.#####./.#...#./#.....#",
      "..###../.#...#./#.....#/#.....#/#.....#/.#...#./..###.."
    };

    void gem(float x, float y, int i) {
      stamp(jewels[i - 1], x + 1, y + 2, 4, 0xff101827);
      stamp(jewels[i - 1], x, y, 4, colors[i]);
    }

    void drawMatch() {
      stat("SCORE", "" + match.score, 24);
      stat(
          match.mode == Games.Match.STEPS
              ? "MOVES"
              : match.mode == Games.Match.TIMED ? "TIME" : "MODE",
          match.mode == Games.Match.STEPS
              ? "" + match.moves
              : match.mode == Games.Match.TIMED ? (int) Math.ceil(match.seconds) + "s" : "FREE",
          153);
      stat("HI", "" + prefs.getInt(key(), 0), 285);
      frame(18, 191, 364, 364, colors[2]);
      for (int y = 0; y < 8; y++)
        for (int x = 0; x < 8; x++) {
          float a = 20 + x * 45, b = 193 + y * 45;
          rect(a + 1, b + 1, 43, 43, (x + y) % 2 == 0 ? 0xff252e46 : 0xff20283e);
          gem(a + 8, b + 8, match.board[y][x]);
          if ((sx == x && sy == y)
              || (hint != null
                  && ((hint[0] == x && hint[1] == y) || (hint[2] == x && hint[3] == y))))
            outline(a + 2, b + 2, 41, 41, lime);
        }
      if (flash > 0) outline(18, 191, 364, 364, colors[2]);
      if (powers) {
        text("道具", 22, 589, 16, ink);
        button(
            "提示 ×" + match.items[0],
            20,
            604,
            112,
            53,
            match.items[0] == 0 ? deep : panel,
            () -> {
              int[] n = match.hint();
              if (n != null) {
                hint = n;
                sx = sy = -1;
                bombArmed = false;
                fx(Feedback.POWER);
                toast("方框标出一组可以交换的宝石。");
              } else toast("提示已用完。");
            });
        button(
            "炸弹 ×" + match.items[1],
            143,
            604,
            112,
            53,
            bombArmed ? lime : match.items[1] == 0 ? deep : panel,
            () -> {
              if (match.items[1] > 0) {
                bombArmed = !bombArmed;
                hint = null;
                sx = sy = -1;
                fx(Feedback.SELECT);
                toast(bombArmed ? "点选一格，清除周围 3 × 3 宝石。" : "已取消放置炸弹。");
              } else toast("炸弹已用完。");
            });
        button(
            "洗牌 ×" + match.items[2],
            266,
            604,
            114,
            53,
            match.items[2] == 0 ? deep : panel,
            () -> {
              if (match.shuffleTool()) {
                hint = null;
                sx = sy = -1;
                bombArmed = false;
                fx(Feedback.POWER);
                toast("已重新排列棋盘。");
              } else toast("洗牌已用完。");
            });
      }
      text(
          messageTime > 0 ? message : bombArmed ? "炸弹已选中：点选中心格。" : "点按两颗相邻宝石，或直接滑动交换。",
          22,
          700,
          14,
          bombArmed ? colors[2] : ink);
      footer("道具不扣步数；没有可用交换时自动重排。");
    }

    void matchCell(int x, int y) {
      if (bombArmed) {
        if (match.bomb(x, y)) {
          bombArmed = false;
          hint = null;
          flash = .18f;
          fx(Feedback.POWER);
          toast("炸弹清除 · " + match.lastChain + " 段连锁");
        }
      } else if (sx == x && sy == y) {
        sx = sy = -1;
      } else if (sx >= 0 && Math.abs(sx - x) + Math.abs(sy - y) == 1) {
        matchSwap(sx, sy, x, y);
      } else {
        sx = x;
        sy = y;
        fx(Feedback.SELECT);
      }
    }

    void matchSwap(int x, int y, int a, int b) {
      boolean ok = match.play(x, y, a, b);
      sx = sy = -1;
      hint = null;
      if (ok) {
        flash = .16f;
        if (!match.over) fx(Feedback.CLEAR);
        toast(match.lastChain > 1 ? match.lastChain + " 段连锁！" : "消除成功。");
      } else toast("至少连成三颗；这次不扣步数。");
    }

    float mineSize() {
      return Math.min(360f / mines.width, 440f / mines.height);
    }

    float mineX() {
      return (400 - mines.width * mineSize()) / 2;
    }

    void drawMines() {
      stat("MINES", "" + (mines.quantity - mines.flagCount()), 24);
      stat("TIME", (int) elapsed + "s", 160);
      stat("CLEAR", mines.revealed + "/" + mines.safeCount(), 280);
      float s = mineSize(), ox = mineX(), oy = 190;
      frame(ox - 2, oy - 2, s * mines.width + 4, s * mines.height + 4, colors[4]);
      for (int y = 0; y < mines.height; y++)
        for (int x = 0; x < mines.width; x++) {
          float a = ox + x * s, b = oy + y * s;
          if (mines.over && mines.mines[y][x] && !mines.won) {
            tile(a, b, s, x == mines.explodedX && y == mines.explodedY ? 0xff984351 : 0xff655064);
            stamp(
                ".###./#####/##.##/#####/.###.",
                a + s * .25f,
                b + s * .25f,
                Math.max(2, (int) (s * .10f)),
                ink);
          } else if (mines.open[y][x]) {
            rect(a + 1, b + 1, s - 2, s - 2, 0xff172336);
            int n = mines.count(x, y);
            if (n > 0) mono("" + n, a + s * .31f, b + s * .70f, s * .55f, colors[1 + (n - 1) % 7]);
          } else {
            tile(a, b, s, 0xff394866);
            if (mines.flags[y][x] || (mines.won && mines.mines[y][x])) {
              rect(a + s * .31f, b + s * .20f, s * .09f, s * .59f, ink);
              rect(a + s * .40f, b + s * .20f, s * .29f, s * .24f, colors[5]);
            }
          }
        }
      button(
          "挖开",
          20,
          648,
          175,
          50,
          flagMode ? panel : lime,
          () -> {
            flagMode = false;
            fx(Feedback.SELECT);
          });
      button(
          "插旗",
          205,
          648,
          175,
          50,
          flagMode ? lime : panel,
          () -> {
            flagMode = true;
            fx(Feedback.SELECT);
          });
      int record =
          prefs.getInt(mode == 0 ? "minesTime" : "mines_" + cols + "_" + rows + "_" + mineCount, 0);
      text(
          mines.width + " × " + mines.height + "   /   " + mines.quantity + " 颗地雷",
          24,
          728,
          14,
          ink);
      if (record > 0) text("最快 " + record + " 秒", 257, 728, 13, colors[4]);
      footer("首点周围安全；旗数匹配时可点数字展开。");
    }

    void mineCell(int x, int y, boolean mark) {
      if (mark) {
        if (mines.flag(x, y)) fx(Feedback.FLAG);
      } else if (mines.reveal(x, y) && !mines.over) fx(Feedback.DROP);
    }

    void drawPong() {
      stat("YOU", "" + pong.score, 24);
      stat("CPU", "" + pong.enemy, 175);
      stat("SPIN", String.format(Locale.ROOT, "%+.1f", pong.spin), 280);
      frame(18, 186, 364, 536, colors[3]);
      c.save();
      c.translate(29, 194);
      c.scale(.95f, .925f);
      rect(0, 0, 360, 560, deep);
      dotField(9, 9, 344, 540, 14, 0xff263048);
      for (int x = 10; x < 355; x += 16) rect(x, 279, 8, 2, 0xff495575);
      rect(5, 7, 2, 546, edge);
      rect(353, 7, 2, 546, edge);
      paddle(pong.ai, 57, 84, colors[3]);
      paddle(pong.player, 493, 84, lime);
      drawSpinningBall(pong.x, pong.y, pong.angle);
      if (pong.serve > 0) {
        rect(121, 218, 118, 37, panel);
        mono("READY", 150, 242, 18, lime);
      }
      c.restore();
      footer("横拖球拍；用边缘击球或甩拍打出弧线。");
    }

    void paddle(float x, float y, int width, int tint) {
      rect(x - width / 2f, y, width, 10, tint);
      rect(x - width / 2f + 2, y + 1, width - 4, 2, 0x55ffffff);
    }

    void drawSpinningBall(float x, float y, float angle) {
      stamp(".####./######/######/######/######/.####.", x - 9, y - 9, 3, ink);
      // Project surface markers from a rotating sphere, hiding those on its far side.
      for (int lane = -1; lane <= 1; lane++)
        for (int j = 0; j < 5; j++) {
          double theta = j * (2 * Math.PI / 5) + angle + lane * .45;
          if (Math.cos(theta) > 0) {
            float px = (float) (Math.sin(theta) * (lane == 0 ? 7 : 5.6)), py = lane * 5;
            rect(
                x + Math.round(px / 2) * 2 - 1,
                y + py - 1,
                3,
                3,
                lane == 0 ? 0xff6c537e : 0xffa58aad);
          }
        }
    }

    void drawSnake() {
      stat("APPLES", "" + snake.score, 24);
      stat("HI", "" + prefs.getInt(key(), 0), 161);
      stat("SPEED", "" + Math.round(1 / snake.interval()) + "/s", 280);
      frame(21, 191, 358, 402, colors[5]);
      int ox = 24, oy = 194, s = 22;
      for (int y = 0; y < Games.Snake.HEIGHT; y++)
        for (int x = 0; x < Games.Snake.WIDTH; x++) {
          rect(ox + x * s, oy + y * s, s - 1, s - 1, (x + y) % 2 == 0 ? deep : 0xff161d31);
          rect(ox + x * s + 10, oy + y * s + 10, 2, 2, edge);
        }
      if (snake.food >= 0) {
        int x = ox + (snake.food % 16) * s, y = oy + (snake.food / 16) * s;
        stamp("..#../.###./#####/#####/.###.", x + 4, y + 3, 3, colors[2]);
      }
      boolean head = true;
      for (int q : snake.body) {
        int x = ox + (q % 16) * s, y = oy + (q / 16) * s;
        tile(x, y, s, head ? lime : colors[4]);
        if (head) {
          rect(x + 7, y + 6, 3, 3, deep);
          rect(x + 13, y + 6, 3, 3, deep);
        }
        head = false;
      }
      int[] arrows = {3, 0, 2, 1};
      String[] glyphs = {"◀", "▲", "▼", "▶"};
      for (int i = 0; i < 4; i++) {
        final int d = arrows[i];
        button(glyphs[i], 22 + i * 91, 648, 82, 58, panel, () -> turnSnake(d));
      }
      if (snakeReady) {
        rect(86, 365, 228, 49, deep);
        outline(86, 365, 228, 49, colors[5]);
        text("滑动或按方向键开始", 106, 396, 18, ink);
      }
      footer("在棋盘上滑动，或点方向键；不能掉头。");
    }

    boolean snakeReady;

    void turnSnake(int direction) {
      if (snakeReady) {
        snakeReady = false;
        fall = 0;
        last = 0;
      }
      snake.turn(direction);
    }

    void drawBreaker() {
      stat("SCORE", "" + breaker.score, 24);
      stat("LIVES", "" + breaker.lives, 161);
      stat("HI", "" + prefs.getInt(key(), 0), 280);
      frame(18, 186, 364, 536, colors[6]);
      c.save();
      c.translate(20, 194);
      rect(0, 0, 360, 510, deep);
      dotField(9, 9, 344, 492, 12, 0xff263048);
      for (int y = 0; y < Games.Breaker.ROWS; y++)
        for (int x = 0; x < Games.Breaker.COLS; x++)
          if (breaker.bricks[y][x]) {
            int a = 8 + x * 43, b = 32 + y * 27;
            rect(a, b, 40, 23, colors[y + 1]);
            rect(a + 2, b + 2, 36, 3, 0x55ffffff);
            rect(a + 2, b + 18, 36, 3, 0x55000000);
          }
      paddle(breaker.paddle, 468, 78, lime);
      tile(breaker.x - 6, breaker.y - 6, 12, ink);
      c.restore();
      if (breaker.ready && !breaker.over)
        button(
            "发球  ▶",
            122,
            521,
            156,
            58,
            lime,
            () -> {
              if (breaker.launch()) fx(Feedback.DROP);
            });
      footer("拖动挡板接球 · 清完所有砖块即通关。");
    }

    void overlay(boolean end) {
      rect(0, 115, 400, 685, 0xe80b101e);
      hits.clear();
      frame(33, 267, 334, 280, colors[game]);
      mono(end ? "ROUND COMPLETE" : "P1  /  PAUSED", 56, 303, 12, colors[game]);
      String title =
          !end
              ? "已暂停"
              : game == 4
                  ? (mines.won ? "扫雷成功" : "踩到地雷")
                  : game == 3
                      ? (won() ? "你赢了" : "电脑获胜")
                      : game == 6 && won() ? "砖块清空" : game == 5 && won() ? "填满全场" : "本局结束";
      text(title, 56, 354, 31, ink);
      text(
          end
              ? (game == 4
                  ? "用时 " + (int) elapsed + " 秒 · 安全格 " + mines.revealed + "/" + mines.safeCount()
                  : "本局得分  " + score())
              : "准备好了，再接着玩。",
          56,
          392,
          15,
          muted);
      button(
          end ? "再来一局" : "继续游戏",
          55,
          420,
          290,
          50,
          lime,
          () -> {
            fx(Feedback.SELECT);
            if (end) start(game);
            else resume(false);
          });
      button(
          "返回大厅",
          55,
          483,
          290,
          42,
          panel,
          () -> {
            fx(Feedback.SELECT);
            leave();
          });
    }

    @Override
    public boolean performClick() {
      super.performClick();
      return true;
    }

    boolean boardTouch;

    @Override
    public boolean onTouchEvent(android.view.MotionEvent event) {
      if (scale <= 0) return true;
      float x = (event.getX() - offsetX) / scale, y = (event.getY() - offsetY) / scale;
      int action = event.getActionMasked();
      if (action == MotionEvent.ACTION_DOWN) {
        downX = x;
        downY = y;
        boardTouch = false;
        for (Hit h : hits)
          if (h.contains(x, y)) {
            performClick();
            h.run.run();
            invalidate();
            return true;
          }
        if (game == 0 || paused || over()) return true;
        boardTouch = true;
      }
      if (!boardTouch || game == 0 || paused || over()) return true;
      if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
        if (game == 3 && downY >= 190 && downY <= 725) pong.aim((x - 29) / .95f);
        if (game == 6 && downY >= 190 && downY <= 725) breaker.aim(x - 20);
        if (game == 5 && downY >= 189 && downY <= 595) {
          float dx = x - downX, dy = y - downY;
          if (Math.max(Math.abs(dx), Math.abs(dy)) > 15) {
            turnSnake(Math.abs(dx) > Math.abs(dy) ? dx > 0 ? 1 : 3 : dy > 0 ? 2 : 0);
            downX = x;
            downY = y;
          }
        }
      }
      if (action == MotionEvent.ACTION_UP) {
        performClick();
        boardTouch = false;
        if (game == 2 && downX >= 20 && downX < 380 && downY >= 193 && downY < 553) {
          int a = (int) ((downX - 20) / 45), b = (int) ((downY - 193) / 45);
          float dx = x - downX, dy = y - downY;
          if (!bombArmed && Math.max(Math.abs(dx), Math.abs(dy)) >= 18) {
            int tx = a, ty = b;
            if (Math.abs(dx) >= Math.abs(dy)) tx += dx > 0 ? 1 : -1;
            else ty += dy > 0 ? 1 : -1;
            matchSwap(a, b, tx, ty);
          } else matchCell(a, b);
        }
        if (game == 4) {
          float sz = mineSize(), left = mineX();
          if (downX >= left
              && downX < left + mines.width * sz
              && downY >= 190
              && downY < 190 + mines.height * sz
              && Math.hypot(x - downX, y - downY) < sz) {
            mineCell(
                (int) ((downX - left) / sz),
                (int) ((downY - 190) / sz),
                flagMode || event.getEventTime() - event.getDownTime() >= 450);
          }
        }
      }
      if (action == MotionEvent.ACTION_CANCEL) boardTouch = false;
      invalidate();
      return true;
    }
  }
}
