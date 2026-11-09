package io.jbnu.test;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;

/** Desktop prototype. All gameplay distances use world units. */
public class Main extends ApplicationAdapter {
    private static final float W = 800, H = 600, MAP_W = 13000;
    private static final float GRAVITY = 1700;
    // Ballistic height h=v^2/(2g): sqrt(1.5) speed produces 1.5x height.
    private static final float HEIGHT_SCALE = 1.5f;
    private static final float JUMP_SCALE = (float)Math.sqrt(HEIGHT_SCALE);
    private SpriteBatch batch;
    private BitmapFont font;
    private OrthographicCamera camera, uiCamera;
    private FitViewport viewport, uiViewport;
    private int rushSpawned, rushTotal;
    private float spawnTimer;
    private boolean gimmickActive;
    private final Rectangle gimmickReceiver = new Rectangle(6250, 310, 28, 30);
    private final Rectangle gimmickGate = new Rectangle(6350, 170, 30, 500);
    private final Rectangle gimmickBridge = new Rectangle(6580, 228, 380, 22);
    private final Array<Rectangle> xpItems = new Array<>();
    private Texture flyingTex;
    private float bossAreaCooldown, bossAreaWarning, bossAreaActive;
    private final Rectangle bossArea = new Rectangle();
    private Texture xpTex;
    private Texture white;
    private Texture groundTopTex;
    private boolean receiverActivated;
    private Texture playerTex, patrolTex, chargerTex, shooterTex, bossTex;
    private Texture blockTex, bulletTex, missileTex, panelOffTex, panelOnTex;
    private Texture deviceTex, gateTex, bridgeTex, padTex, exitTex;
    private final Array<Texture> owned = new Array<>();
    private final Texture[] backgrounds = new Texture[3];
    private final Rectangle player = new Rectangle(80, 60, 32, 44);
    private final Rectangle device = new Rectangle(1510, 60, 42, 65);
    private final Rectangle gate = new Rectangle(2050, 60, 28, 200);
    private final Rectangle bridge = new Rectangle(12000, 20, 260, 40);
    private final Rectangle pad = new Rectangle(2140, 60, 100, 12);
    private final Rectangle exit = new Rectangle(2470, 60, 40, 90);
    private final Rectangle boss = new Rectangle(2290, 60, 110, 150);
    private final Array<Rectangle> blocks = new Array<>();
    private final Array<Enemy> enemies = new Array<>();
    private final Array<Shot> shots = new Array<>();
    private final Array<Panel> panels = new Array<>();
    private enum Mode { PLAY, PAUSE, CHOOSE, DEAD, CLEAR }
    private enum Rush { IDLE, WARNING, FIGHT, DONE }
    private Mode mode = Mode.PLAY;
    private Rush rush = Rush.IDLE;
    private int stage = 0, hp = 20, maxHp = 20, damage = 1, level = 1, xp = 0, nextXp = 4;
    private int facing = 1, pendingChoices = 0, wave = 0;
    private float vx, vy, fireTimer, invincible, dashTimer, dashCooldown;
    private float coyote, jumpBuffer, warningTimer, waveTimer, padCooldown;
    private float chargeGrace;
    private float fireInterval = .22f, panelDuration = 6, dashPeriod = .8f;
    private boolean grounded, bossAlive;
    private int bossHp;
    private float bossClock, bossFireTimer;
    private final int[] options = new int[3];
    private final String[] upgradeNames = {
        "Power +1", "Fire rate +", "Max HP +4 / Heal +4", "Dash recovery +", "Dash recovery / Heal +4", "Heal +12"
    };
    // Snapshot at stage entry: retry does not retain experience earned in a failed attempt.
    private int savedMaxHp, savedDamage, savedLevel, savedXp, savedNextXp;
    private float savedFireInterval, savedPanelDuration, savedDashPeriod;

    private static class Enemy {
        Rectangle box;
        int type, hp;
        float origin, vy, timer;
        float attackTimer = 1.2f, windup;
        float aimX, aimY;
        int direction = 1, state;
        boolean rushEnemy, noticed;
        Enemy(float x, float y, int type, boolean rushEnemy) {
            box = new Rectangle(x, y, type == 1 ? 42 : 36, 40);
            origin = x; this.type = type; this.rushEnemy = rushEnemy;
            hp = type == 1 ? 5 : 3;
        }
    }
    private static class Shot {
        Rectangle box;
        float vx, vy, life = 3;
        int damage;
        boolean hostile, missile;
        Shot(float x, float y, float vx, float vy, int damage, boolean hostile, boolean missile) {
            box = new Rectangle(x, y, missile ? 20 : 12, missile ? 12 : 6);
            this.vx = vx; this.vy = vy; this.damage = damage;
            this.hostile = hostile; this.missile = missile;
            if (!hostile) life = 20;
        }
    }
    private static class Panel {
        Rectangle floor, receiver;
        float timer;
        Panel(float x) {
            floor = new Rectangle(x, 60, 220, 8);
            receiver = new Rectangle(x + 100, 78, 24, 30);
        }
    }

    @Override public void create() {
        batch = new SpriteBatch(); font = new BitmapFont();
        camera = new OrthographicCamera(); uiCamera = new OrthographicCamera();
        viewport = new FitViewport(W, H, camera);
        uiViewport = new FitViewport(W, H, uiCamera);
        Pixmap pix = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pix.setColor(Color.WHITE); pix.fill(); white = new Texture(pix); pix.dispose(); owned.add(white);
        playerTex = load("sprites/player.png");
        xpTex = load("objects/xp_orb.png");
        flyingTex = load("sprites/enemy_flying.png");
        patrolTex = load("sprites/enemy_patrol.png");
        chargerTex = load("sprites/enemy_charger.png");
        shooterTex = load("sprites/enemy_shooter.png");
        bossTex = load("sprites/boss.png");
        groundTopTex = load("tiles/ground_top.png");
        blockTex = load("tiles/block.png"); bulletTex = load("sprites/bullet.png");
        missileTex = load("sprites/missile.png");
        panelOffTex = load("objects/panel_off.png"); panelOnTex = load("objects/panel_on.png");
        deviceTex = load("objects/device.png"); gateTex = load("objects/gate.png");
        bridgeTex = load("objects/bridge.png"); padTex = load("objects/jump_pad.png");
        exitTex = load("objects/exit.png");
        for (int i = 0; i < 3; i++) backgrounds[i] = load("backgrounds/stage" + (i + 1) + ".png");
        saveBuild(); loadStage();
    }
    private Texture load(String path) {
        if (!Gdx.files.internal(path).exists()) return white;
        Texture t = new Texture(Gdx.files.internal(path)); owned.add(t); return t;
    }
    private void saveBuild() {
        savedMaxHp = maxHp; savedDamage = damage; savedLevel = level;
        savedXp = xp; savedNextXp = nextXp; savedFireInterval = fireInterval;
        savedPanelDuration = panelDuration; savedDashPeriod = dashPeriod;
    }
    private void retry() {
        maxHp = savedMaxHp; damage = savedDamage; level = savedLevel;
        xp = savedXp; nextXp = savedNextXp; fireInterval = savedFireInterval;
        panelDuration = savedPanelDuration; dashPeriod = savedDashPeriod;
        loadStage();
    }
    private void loadStage() {
        blocks.clear(); enemies.clear(); shots.clear(); panels.clear();
        mode = Mode.PLAY; rush = Rush.IDLE; receiverActivated = false; pendingChoices = 0; wave = 0;
        hp = maxHp; player.setPosition(80, 60); vx = vy = 0; grounded = false;
        fireTimer = invincible = dashTimer = dashCooldown = coyote = jumpBuffer = padCooldown = 0;
        facing = 1; chargeGrace = 0; bossClock = bossFireTimer = 0; bossHp = 200; bossAlive = stage == 2;
        gimmickReceiver.set(6250, 310, 28, 30);
        gimmickGate.set(6350, 170, 30, 500);
        gimmickBridge.set(6580, 228, 380, 22);
        gate.height = 200;
        buildLongTerrain();
        // Horizontal-only world scale: preserve character sizes and jump heights.
        for (Rectangle b : blocks) { b.x *= 2; b.width *= 2; }
        for (Enemy e : enemies) { e.box.x *= 2; e.origin *= 2; }
        for (Panel p : panels) {
            p.floor.x *= 2; p.floor.width *= 2; p.receiver.x *= 2;
        }
        rebuildVerticalEntry();
        widenEnemyPlatforms();
        // Three creatures per original spawn: preserve the supporting ledge for ground types.
        int originalCount = enemies.size;
        for (int i = 0; i < originalCount; i++) {
            Enemy source = enemies.get(i);
            float min = source.box.x, max = source.box.x;
            for (Rectangle b : blocks) if (Math.abs(b.y + b.height - source.box.y) < 1
                && source.box.x + 18 >= b.x && source.box.x + 18 <= b.x + b.width) {
                min = b.x + 4; max = b.x + b.width - 46; break;
            }
            float groundX = MathUtils.clamp(source.box.x + 54, min, Math.max(min, max));
            enemies.add(new Enemy(groundX, source.box.y, (source.type + 1) % 3, false));
            if (stage >= 1) enemies.add(new Enemy(source.box.x - 35, source.box.y + 140, 3, false));
            else enemies.add(new Enemy(MathUtils.clamp(source.box.x - 45, min, Math.max(min, max)),
                source.box.y, (source.type + 2) % 3, false));
        }
        for (Panel p : panels) {
            p.floor.width /= 3f;
            // End the strip 10 units before the takeoff ledge at x=4200.
            // The mandatory wall starts at x=4260, so jump directly from the charged edge.
            p.floor.x = 4190 - p.floor.width;
            p.receiver.x = p.floor.x + p.floor.width * .5f - p.receiver.width * .5f;
        }
        bossAreaCooldown = 2; bossAreaWarning = bossAreaActive = 0;
        rushSpawned = 0; rushTotal = (2 + stage) * 2 * 5 * 5; spawnTimer = .4f;
        gimmickActive = false; xpItems.clear();
        gimmickReceiver.y = stage == 2 ? 350 : 190;
        if (stage > 0) {
            // Remove a single lower route platform: restored only by the stage-3 receiver.
            if (stage == 2) for (int i = blocks.size - 1; i >= 0; i--)
                if (Math.abs(blocks.get(i).x - 6580) < 1) blocks.removeIndex(i);
            float[][] items = {{1400,220},{2480,220},{5600,250},{7400,330},{8600,240}};
            for (float[] at : items) {
                float floor = surfaceAt(at[0]);
                if (floor > 0) xpItems.add(new Rectangle(at[0], floor + 16, 22, 22));
            }
        }
        // Progress device occurs late in the route; finale positions scale with map length.
        device.setPosition(10020, 60);
        gate.setPosition(12000, 60);
        bridge.set(12000, 20, 520, 40);
        pad.setPosition(12500, 60);
        boss.setPosition(12600, 60);
        exit.setPosition(stage == 2 ? 12900 : 12820, stage == 2 ? 325 : 60);
        scaleVerticalLayout();
        alignReceiversWithShots();
        updateCamera();
    }
    private void alignReceiversWithShots() {
        // Player bullets spawn at footY+21 with height 6. Keep receiver centered at +24.
        for (Panel p : panels) p.receiver.y = p.floor.y + 24 - p.receiver.height / 2;
        device.y = 60 + 24 - device.height / 2;
        if (stage > 0) {
            float support = -1000;
            float centerX = gimmickReceiver.x + gimmickReceiver.width / 2;
            for (Rectangle b : blocks) if (centerX >= b.x && centerX <= b.x + b.width)
                support = Math.max(support, b.y + b.height);
            if (support > -999) gimmickReceiver.y = support + 24 - gimmickReceiver.height / 2;
        }
    }
    private float scaledY(float y) { return 60 + (y - 60) * HEIGHT_SCALE; }
    private void moveObjectUp(Rectangle r) { r.y = scaledY(r.y); }
    private void scaleVerticalLayout() {
        // Ground stays anchored at y=0; thin platform thickness stays readable.
        for (Rectangle r : blocks) {
            float newTop = scaledY(r.y + r.height);
            if (r.y == 0) r.height = newTop;
            else r.y = newTop - r.height;
        }
        for (Enemy e : enemies) moveObjectUp(e.box);
        for (Panel p : panels) { moveObjectUp(p.floor); moveObjectUp(p.receiver); }
        for (Rectangle item : xpItems) moveObjectUp(item);
        moveObjectUp(device); moveObjectUp(gate); gate.height *= HEIGHT_SCALE;
        moveObjectUp(bridge); // top remains 60 because the bottom bridge thickens downward
        bridge.height = 60 - bridge.y;
        moveObjectUp(pad); moveObjectUp(boss); moveObjectUp(exit);
        moveObjectUp(gimmickReceiver); moveObjectUp(gimmickGate);
        gimmickGate.height *= HEIGHT_SCALE;
        float bridgeTop = scaledY(gimmickBridge.y + gimmickBridge.height);
        gimmickBridge.y = bridgeTop - gimmickBridge.height;
    }
    @Override public void render() {
        float dt = Math.min(Gdx.graphics.getDeltaTime(), 1f / 30f);
        if (Gdx.input.isKeyJustPressed(Input.Keys.ENTER)) {
            if (mode == Mode.CLEAR) {
                stage = 0; maxHp = 20; damage = 1; level = 1; xp = 0; nextXp = 4;
                fireInterval = .22f; panelDuration = 6; dashPeriod = .8f; saveBuild(); loadStage();
            } else retry();
        } else if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            if (mode == Mode.PLAY) mode = Mode.PAUSE;
            else if (mode == Mode.PAUSE) mode = Mode.PLAY;
        }
        if (mode == Mode.CHOOSE) {
            int selected = Gdx.input.isKeyJustPressed(Input.Keys.NUM_1) ? 0
                : Gdx.input.isKeyJustPressed(Input.Keys.NUM_2) ? 1
                : Gdx.input.isKeyJustPressed(Input.Keys.NUM_3) ? 2 : -1;
            if (selected >= 0) {
                applyUpgrade(options[selected]); pendingChoices--;
                if (pendingChoices > 0) rollChoices(); else mode = Mode.PLAY;
            }
        } else if (mode == Mode.PLAY) update(dt);
        draw();
    }
    private boolean fever() { return rush == Rush.FIGHT; }
    private void update(float dt) {
        if (rush == Rush.WARNING) {
            warningTimer -= dt;
            if (warningTimer <= 0) { rush = Rush.FIGHT; waveTimer = .5f; }
            return;
        }
        invincible = Math.max(0, invincible - dt);
        fireTimer = Math.max(0, fireTimer - dt);
        dashCooldown = Math.max(0, dashCooldown - dt);
        padCooldown = Math.max(0, padCooldown - dt);
        // Charge receivers latch until stage restart; activated receivers pass all later bullets.
        coyote = grounded ? .1f : Math.max(0, coyote - dt);
        jumpBuffer = Math.max(0, jumpBuffer - dt);
        if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) jumpBuffer = .1f;
        float input = (Gdx.input.isKeyPressed(Input.Keys.RIGHT) ? 1 : 0)
            - (Gdx.input.isKeyPressed(Input.Keys.LEFT) ? 1 : 0);
        if (input != 0) facing = input > 0 ? 1 : -1;
        chargeGrace = Math.max(0, chargeGrace - dt);
        for (Panel p : panels) if (p.timer > 0 && player.x + player.width > p.floor.x
            && player.x < p.floor.x + p.floor.width && Math.abs(player.y - p.floor.y) < 12)
            chargeGrace = .25f;
        float speed = chargeGrace > 0 ? 364 : 260;
        if (Gdx.input.isKeyJustPressed(Input.Keys.X) && dashCooldown <= 0) {
            dashTimer = .15f; dashCooldown = dashPeriod;
        }
        vx = dashTimer > 0 ? facing * 650 : input * speed;
        dashTimer = Math.max(0, dashTimer - dt);
        if (jumpBuffer > 0 && coyote > 0) { vy = (chargeGrace > 0 ? 1000 : 720) * JUMP_SCALE; coyote = jumpBuffer = 0; grounded = false; }
        vy = Math.max(-900, vy - GRAVITY * dt);
        movePlayer(dt);
        if (player.y < -100) { mode = Mode.DEAD; return; }
        if (stage == 2 && rush == Rush.DONE && player.overlaps(pad) && padCooldown <= 0) {
            vy = 1050 * JUMP_SCALE; grounded = false; padCooldown = .6f;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.Z) && fireTimer <= 0) {
            shots.add(new Shot(facing > 0 ? player.x + player.width : player.x - 12,
                player.y + 21, facing * 700, 0, damage * (fever() ? 2 : 1), false, false));
            fireTimer = fireInterval * (fever() ? .38f : 1);
        }
        for (int i = xpItems.size - 1; i >= 0; i--) if (player.overlaps(xpItems.get(i))) {
            xpItems.removeIndex(i);
            // Exactly one level per pickup, preserving progress toward the following level.
            level++; nextXp += 2; pendingChoices++;
        }
        updateShots(dt);
        if (rush == Rush.WARNING) return;
        updateEnemies(dt);
        if (mode != Mode.PLAY) { updateCamera(); return; }
        updateRush(dt);
        if (bossAlive && rush == Rush.DONE) updateBoss(dt);
        if (mode != Mode.PLAY) { updateCamera(); return; }
        if (pendingChoices > 0 && rush != Rush.FIGHT) { mode = Mode.CHOOSE; rollChoices(); }
        if (mode == Mode.PLAY && rush == Rush.DONE && !bossAlive && player.overlaps(exit)) {
            if (stage < 2) { stage++; saveBuild(); loadStage(); return; }
            mode = Mode.CLEAR;
        }
        updateCamera();
    }
    // 50 authored terrain silhouettes. No modulo-based recycling of map sections.
    private void buildLongTerrain() {
        // No floor underneath the floating routes: these are mandatory platform jumps.
        // Each row: x, topY, width; a solid cliff is explicitly marked below.
        float[][] route;
        if (stage == 0) route = new float[][] {
            {0,60,340}, {400,130,180}, {650,210,180}, {910,150,200},
            {1190,230,180}, {1450,140,200}, {1720,60,380},
            {2130,250,250}, {2460,300,190}, {2730,220,220},
            {3030,290,170}, {3280,200,220}, {3580,120,220},
            {3880,200,210}, {4170,280,220}, {4470,200,200}, {4740,120,160}
        };
        else if (stage == 1) route = new float[][] {
            {0,60,340}, {410,140,190}, {680,220,170}, {930,300,180},
            {1190,220,190}, {1460,140,190}, {1720,60,380},
            {2130,250,250}, {2460,330,190}, {2730,250,180},
            {2990,170,210}, {3280,250,200}, {3560,330,180},
            {3820,240,210}, {4110,160,220}, {4410,240,220}, {4710,150,190}
        };
        else route = new float[][] {
                {0,60,340}, {400,140,170}, {650,220,190}, {920,140,190},
                {1190,220,180}, {1450,140,200}, {1720,60,380},
                {2130,250,250}, {2460,170,210}, {2750,250,190},
                {3020,330,190}, {3290,250,190}, {3560,170,200},
                {3840,250,190}, {4110,330,190}, {4380,240,210}, {4670,150,230}
            };
        for (int i = 0; i < route.length; i++) {
            float[] r = route[i];
            if (i == 0 || i == 6 || i == 7) ground(r[0], r[2], r[1]);
            else blocks.add(new Rectangle(r[0], r[1] - 22, r[2], 22));
            // Monsters stand on elevated platforms, not only on the lowest floor.
            if (i == 2 || i == 4 || i == 9 || i == 12 || i == 15)
                enemies.add(new Enemy(r[0] + r[2] / 2 - 18, r[1], i % 3, false));
        }
        Panel charge = new Panel(1840);
        charge.floor.set(1840, 60, 250, 8);
        charge.receiver.set(1960, 78, 24, 30);
        panels.add(charge);
        // Ground and elevated combat platforms for the final rush.
        ground(4900, 1100, 60);
        blocks.add(new Rectangle(5100, 155, 370, 22));
        blocks.add(new Rectangle(5460, 240, 460, 22));
        if (stage == 1) ground(6260, 240, 60);
        else ground(6000, 500, 60);
        if (stage == 2) blocks.add(new Rectangle(6320, 300, 180, 25));
    }
    private void widenEnemyPlatforms() {
        // Ground support only. Process each unique ledge once, even with several enemies.
        Array<Rectangle> occupied = new Array<>();
        for (Enemy e : enemies) {
            for (Rectangle b : blocks) {
                if (b.y == 0 || Math.abs(b.y + b.height - e.box.y) > 1) continue;
                if (e.box.x + e.box.width <= b.x || e.box.x >= b.x + b.width) continue;
                if (!occupied.contains(b, true)) occupied.add(b);
                break;
            }
        }
        for (Rectangle b : occupied) {
            float oldX = b.x, oldRight = b.x + b.width;
            float left = Math.max(0, oldX - b.width * .9f);
            float right = Math.min(MAP_W, oldRight + b.width * .9f);
            float top = b.y + b.height;
            // Keep gaps next to same-level ledges. Do not seal a neighboring ascent.
            for (Rectangle other : blocks) {
                if (other == b) continue;
                float otherTop = other.y + other.height;
                if (Math.abs(otherTop - top) < 1) {
                    if (other.x + other.width <= oldX) left = Math.max(left, other.x + other.width + 24);
                    if (other.x >= oldRight) right = Math.min(right, other.x - 24);
                } else if (otherTop > top && other.y < top + 140) {
                    if (other.x + other.width <= oldX) left = Math.max(left, other.x + other.width + 12);
                    if (other.x >= oldRight) right = Math.min(right, other.x - 12);
                }
            }
            // Never shrink the original collision footprint.
            b.x = Math.min(oldX, left);
            b.width = Math.max(oldRight, right) - b.x;
        }
    }
    private void rebuildVerticalEntry() {
        for (int i = blocks.size - 1; i >= 0; i--)
            if (blocks.get(i).x < 3440) blocks.removeIndex(i);
        for (int i = enemies.size - 1; i >= 0; i--)
            if (enemies.get(i).box.x < 3440) enemies.removeIndex(i);
        ground(0, 360, 60);
        // Ascend right, traverse left, climb again, then cross high and descend.
        // Each successive landing rises 90 units; no bottom floor bypass.
        float[][] route = {
            {430,150,180},{660,240,180},{890,330,180},{1120,420,210},
            {890,510,180},{660,600,180},{430,690,180},{660,780,180},
            {890,870,180},{1120,960,210},{1400,960,220},{1700,870,220},
            {1990,780,220},{2280,690,220},{2570,600,220},{2860,510,220},
            {3120,420,220},{2860,330,220},{3120,240,220},{3380,150,150}
        };
        // Stage offsets vary upper-route elevations but preserve <= 100-unit ascents.
        float lift = stage * 8;
        for (int i = 0; i < route.length; i++) {
            float[] r = route[i]; float top = r[1] + (i < 17 ? lift : 0);
            // Stage 1 only: 90 -> 100 before global 1.5x height scaling (135 -> 150).
            if (stage == 0) top = 60 + (top - 60) * (100f / 90f);
            blocks.add(new Rectangle(r[0], top - 20, r[2], 20));
            if (i == 3 || i == 6 || i == 10 || i == 13 || i == 16)
                enemies.add(new Enemy(r[0] + r[2] / 2 - 18, top, i == 10 || i == 13 ? 2 : i % 3, false));
        }
    }
    private void ground(float x, float width, float top) {
        blocks.add(new Rectangle(x, 0, width, top));
    }
    private float supportNear(float x, float footY) {
        float best = -1000;
        for (Rectangle b : blocks) if (x >= b.x && x < b.x + b.width
            && Math.abs(b.y + b.height - footY) < 14)
            best = Math.max(best, b.y + b.height);
        return best;
    }
    private float surfaceAt(float x) {
        float top = -1000;
        for (Rectangle b : blocks) if (x >= b.x && x < b.x + b.width)
            top = Math.max(top, b.y + b.height);
        return top;
    }
    private void activateReceiver() {
        if (receiverActivated || rush != Rush.IDLE) return;
        receiverActivated = true;
        enemies.clear();
        // Preserve bullets: once activated, the receiver never blocks subsequent shots.
        player.x = MathUtils.clamp(player.x, 9860, 11900);
        rush = Rush.WARNING; warningTimer = 1.2f; updateCamera();
    }
    private boolean near(Rectangle a, Rectangle b, float distance) {
        return Math.abs(a.x + a.width / 2 - b.x - b.width / 2) < distance
            && Math.abs(a.y - b.y) < 100;
    }
    private boolean solid(Rectangle box) {
        if (stage == 1 && !gimmickActive && box.overlaps(gimmickGate)) return true;
        if (stage == 2 && gimmickActive && box.overlaps(gimmickBridge)) return true;
        for (Rectangle b : blocks) if (box.overlaps(b)) return true;
        if (stage == 0 && rush != Rush.DONE && box.overlaps(gate)) return true;
        if (stage == 1 && rush == Rush.DONE && box.overlaps(bridge)) return true;
        return false;
    }
    private Array<Rectangle> collisionBlocks() {
        Array<Rectangle> all = new Array<>(); all.addAll(blocks);
        if (stage == 1 && !gimmickActive) all.add(gimmickGate);
        if (stage == 2 && gimmickActive) all.add(gimmickBridge);
        if (stage == 0 && rush != Rush.DONE) all.add(gate);
        if (stage == 1 && rush == Rush.DONE) all.add(bridge);
        return all;
    }
    private void movePlayer(float dt) {
        Array<Rectangle> solids = collisionBlocks();
        int steps = Math.max(1, (int)Math.ceil(Math.max(Math.abs(vx), Math.abs(vy)) * dt / 6));
        float sub = dt / steps; grounded = false;
        for (int i = 0; i < steps; i++) {
            float dx = vx * sub; player.x += dx;
            for (Rectangle b : solids) if (player.overlaps(b)) {
                if (dx > 0) player.x = b.x - player.width;
                else if (dx < 0) player.x = b.x + b.width;
            }
            float dy = vy * sub; player.y += dy;
            for (Rectangle b : solids) if (player.overlaps(b)) {
                if (dy < 0) { player.y = b.y + b.height; grounded = true; vy = 0; }
                else if (dy > 0) { player.y = b.y - player.height; vy = 0; }
            }
        }
        player.x = MathUtils.clamp(player.x, 0, MAP_W - player.width);
        if (rush == Rush.FIGHT) player.x = MathUtils.clamp(player.x, 9800, 12000 - player.width);
    }
    private void updateEnemies(float dt) {
        Array<Rectangle> solids = collisionBlocks();
        for (Enemy e : enemies) {
            if (!e.rushEnemy && Math.abs(e.box.x - player.x) > 1100) continue;
            if (e.type == 3) {
                // Flying contact enemy: flight explicitly tracks the player; ground patrols do not.
                e.timer += dt;
                float dx = player.x + 16 - e.box.x - 18;
                float dy = player.y + 22 - e.box.y - 20;
                float distance = (float)Math.sqrt(dx * dx + dy * dy);
                if (distance > 2) {
                    float speed = e.rushEnemy ? 155 : 110;
                    e.box.x += dx / distance * speed * dt;
                    e.box.y += dy / distance * speed * dt;
                }
                e.box.y += MathUtils.sin(e.timer * 5) * 12 * dt;
                if (e.box.overlaps(player)) hurt();
                continue;
            }
            // Patrol only: no position/speed/direction decision uses the player.
            e.noticed = false;
            e.state = 0;
            float patrolSpeed = e.type == 1 ? 75 : e.type == 2 ? 50 : 60;
            if (e.rushEnemy) {
                if (e.box.x <= 9800) e.direction = 1;
                if (e.box.x >= 11940) e.direction = -1;
                patrolSpeed = 140;
            } else {
                if (e.box.x <= e.origin - 170) e.direction = 1;
                if (e.box.x >= e.origin + 170) e.direction = -1;
            }
            if (e.type == 2) {
                e.attackTimer -= dt;
                if (e.windup > 0) {
                    e.windup -= dt;
                    if (e.windup <= 0) {
                        shots.add(new Shot(e.box.x + e.box.width / 2, e.box.y + 24,
                            e.aimX * 270, e.aimY * 270, 1, true, false));
                        e.attackTimer = 1.6f;
                    }
                } else if (e.attackTimer <= 0) {
                    float ax = player.x + 16 - e.box.x - e.box.width / 2;
                    float ay = player.y + 22 - e.box.y - 24;
                    float distance = (float)Math.sqrt(ax * ax + ay * ay);
                    if (distance > 1 && distance < 700) {
                        e.aimX = ax / distance; e.aimY = ay / distance;
                        e.windup = .5f;
                    }
                }
            }
            float speed = e.direction * patrolSpeed;
            float nextEdge = e.direction > 0
                ? e.box.x + e.box.width + Math.abs(speed * dt) + 3
                : e.box.x - Math.abs(speed * dt) - 3;
            float support = supportNear(nextEdge, e.box.y);
            // Reverse before stepping off an edge or walking into a higher terrace.
            if (support < e.box.y - 12 || support > e.box.y + 12) {
                e.direction *= -1; speed = e.direction * patrolSpeed;
            }
            e.box.x += speed * dt;
            for (Rectangle b : solids) if (e.box.overlaps(b)) {
                if (speed > 0) e.box.x = b.x - e.box.width;
                else if (speed < 0) e.box.x = b.x + b.width;
                e.direction *= -1;
            }
            if (e.rushEnemy) e.box.x = MathUtils.clamp(e.box.x, 9800, 11950);
            e.vy = Math.max(-900, e.vy - GRAVITY * dt);
            float ey = e.vy * dt; e.box.y += ey;
            for (Rectangle b : solids) if (e.box.overlaps(b)) {
                if (ey < 0) { e.box.y = b.y + b.height; e.vy = 0; }
                else if (ey > 0) { e.box.y = b.y - e.box.height; e.vy = 0; }
            }
            if (e.box.overlaps(player)) hurt();
            if (e.box.y < -100) e.hp = 0;
        }
        for (int i = enemies.size - 1; i >= 0; i--) if (enemies.get(i).hp <= 0) killEnemy(i);
    }
    private void killEnemy(int index) {
        Enemy e = enemies.get(index);
        // Charge puzzle requires shooting its receiver, never enemy kills.
        enemies.removeIndex(index); gainXp(1);
    }
    private void gainXp(int amount) {
        xp += amount;
        while (xp >= nextXp) { xp -= nextXp; level++; nextXp += 2; pendingChoices++; }
    }
    private void hurt() {
        if (invincible > 0 || mode != Mode.PLAY) return;
        hp--; invincible = 1;
        if (hp <= 0) mode = Mode.DEAD;
    }
    private void updateShots(float dt) {
        // Reverse iteration permits safe removal. Enemy bullets added later move next frame.
        for (int i = shots.size - 1; i >= 0; i--) {
            Shot s = shots.get(i); s.life -= dt;
            if (s.missile) {
                float dx = player.x + 16 - s.box.x, dy = player.y + 22 - s.box.y;
                float target = MathUtils.atan2(dy, dx);
                float angle = MathUtils.atan2(s.vy, s.vx);
                float difference = MathUtils.atan2(MathUtils.sin(target - angle), MathUtils.cos(target - angle));
                angle += MathUtils.clamp(difference, -1.5f * dt, 1.5f * dt);
                s.vx = MathUtils.cos(angle) * 180; s.vy = MathUtils.sin(angle) * 180;
            }
            boolean remove = s.life <= 0;
            int n = Math.max(1, (int)Math.ceil(Math.max(Math.abs(s.vx), Math.abs(s.vy)) * dt / 4));
            for (int k = 0; k < n && !remove; k++) {
                s.box.x += s.vx * dt / n; s.box.y += s.vy * dt / n;
                if (!s.hostile) {
                    if (stage > 0 && !gimmickActive && s.box.overlaps(gimmickReceiver)) {
                        gimmickActive = true; remove = true;
                    }
                    for (Panel p : panels) if (!remove && p.timer <= 0 && s.box.overlaps(p.receiver)) {
                        p.timer = Float.POSITIVE_INFINITY; remove = true; break;
                    }
                    if (!remove && !receiverActivated && rush == Rush.IDLE && s.box.overlaps(device)) {
                        activateReceiver(); remove = true;
                    }
                }
                if (!remove && solid(s.box)) remove = true;
                if (!remove && s.hostile && s.box.overlaps(player)) { hurt(); remove = true; }
                if (!remove && !s.hostile) {
                    for (Enemy e : enemies) if (e.hp > 0 && s.box.overlaps(e.box)) {
                        e.hp -= s.damage; remove = true; break;
                    }
                    if (!remove && bossAlive && rush == Rush.DONE && s.box.overlaps(boss)) {
                        bossHp -= s.damage;
                        remove = true;
                        if (bossHp <= 0) { bossAlive = false; gainXp(5); }
                    }
                    if (!remove) for (Shot other : shots) {
                        if (other.hostile && other.missile && other.life > 0 && s.box.overlaps(other.box)) {
                            other.life = 0; remove = true; break;
                        }
                    }
                }
                if (s.box.x < -40 || s.box.x > MAP_W + 40 || s.box.y < -100 || s.box.y > 2300) remove = true;
            }
            if (remove) shots.removeIndex(i);
        }
        for (int i = enemies.size - 1; i >= 0; i--) if (enemies.get(i).hp <= 0) killEnemy(i);
    }
    private void updateRush(float dt) {
        if (rush != Rush.FIGHT) return;
        spawnTimer -= dt;
        if (rushSpawned < rushTotal && spawnTimer <= 0) {
            // Read current player position, not last frame's camera, to guarantee offscreen birth.
            float center = MathUtils.clamp(player.x + 16, W / 2, MAP_W - W / 2);
            float left = center - W / 2 - 100;
            float right = center + W / 2 + 100;
            boolean useLeft = rushSpawned % 2 == 0;
            if (left < 9800) useLeft = false;
            if (right > 11950) useLeft = true;
            float x = useLeft ? left : right;
            int kind = rushSpawned % (stage >= 1 ? 4 : 3);
            Enemy e = new Enemy(x, kind == 3 ? Math.max(170, player.y + 100) : 60, kind, true);
            e.direction = useLeft ? 1 : -1;
            enemies.add(e); rushSpawned++; spawnTimer += .13f;
        }
        boolean alive = false;
        for (Enemy e : enemies) if (e.rushEnemy) { alive = true; break; }
        if (rushSpawned == rushTotal && !alive) {
            rush = Rush.DONE; shots.clear(); hp = Math.min(maxHp, hp + 8);
        }
    }
    private void updateBoss(float dt) {
        bossClock += dt; bossFireTimer -= dt;
        if (bossFireTimer <= 0) {
            Shot missile = new Shot(boss.x, boss.y + 105, -180, 0, 1, true, true);
            missile.life = 80; // Remains a threat even when player stays far from the boss.
            shots.add(missile); bossFireTimer = 1.8f;
        }
        if (bossAreaWarning > 0) {
            bossAreaWarning -= dt;
            if (bossAreaWarning <= 0) bossAreaActive = .45f;
        } else if (bossAreaActive > 0) {
            bossAreaActive -= dt;
            if (player.overlaps(bossArea)) hurt();
        } else {
            bossAreaCooldown -= dt;
            if (bossAreaCooldown <= 0) {
                // Lock onto the current supporting surface, including high platforms.
                float floor = 60;
                for (Rectangle platform : blocks) {
                    float top = platform.y + platform.height;
                    if (platform.x <= player.x + 16 && platform.x + platform.width >= player.x + 16
                        && top <= player.y + 5) floor = Math.max(floor, top);
                }
                bossArea.set(MathUtils.clamp(player.x + 16 - 160, 0, MAP_W - 320), floor, 320, 60);
                bossAreaWarning = 1.1f; bossAreaCooldown = 3.2f;
            }
        }
        if (player.overlaps(boss)) hurt();
    }
    private void rollChoices() {
        options[0] = MathUtils.random(5);
        do { options[1] = MathUtils.random(5); } while (options[1] == options[0]);
        do { options[2] = MathUtils.random(5); } while (options[2] == options[0] || options[2] == options[1]);
    }
    private void applyUpgrade(int choice) {
        switch (choice) {
            case 0: damage++; break;
            case 1: fireInterval = Math.max(.08f, fireInterval * .85f); break;
            case 2: maxHp += 4; hp = Math.min(maxHp, hp + 4); break;
            case 3: dashPeriod = Math.max(.35f, dashPeriod - .1f); break;
            case 4: dashPeriod = Math.max(.35f, dashPeriod - .05f); hp = Math.min(maxHp, hp + 4); break;
            default: hp = Math.min(maxHp, hp + 12); break;
        }
    }
    private void updateCamera() {
        camera.position.set(MathUtils.clamp(player.x + 16, W / 2, MAP_W - W / 2),
            MathUtils.clamp(player.y + player.height / 2, H / 2, 2200 - H / 2), 0);
        camera.update();
    }
    private void image(Texture t, Rectangle r, Color fallback, boolean flip) {
        batch.setColor(t == white ? fallback : Color.WHITE);
        batch.draw(t, r.x, r.y, r.width, r.height, 0, 0, t.getWidth(), t.getHeight(), flip, false);
        batch.setColor(Color.WHITE);
    }
    private void rect(float x, float y, float w, float h, Color c) {
        batch.setColor(c); batch.draw(white, x, y, w, h); batch.setColor(Color.WHITE);
    }
    private void drawTerrain(Rectangle b) {
        if (b.x + b.width < camera.position.x - 430 || b.x > camera.position.x + 430) return;
        // Repeat rather than stretch texture; clipped edge tiles preserve a constant scale.
        Color body = stage == 0 ? new Color(.24f,.20f,.14f,1)
            : stage == 1 ? new Color(.30f,.40f,.48f,1) : new Color(.17f,.19f,.28f,1);
        batch.setColor(blockTex == white ? body : Color.WHITE);
        for (float x = b.x + Math.max(0, (int)((camera.position.x - 430 - b.x) / 64)) * 64; x < Math.min(b.x + b.width, camera.position.x + 430); x += 64) {
            for (float y = b.y; y < b.y + b.height; y += 64) {
                float tw = Math.min(64, b.x + b.width - x);
                float th = Math.min(64, b.y + b.height - y);
                batch.draw(blockTex, x, y, tw, th, 0f, th / 64f, tw / 64f, 0f);
            }
        }
        batch.setColor(Color.WHITE);
        Color top = stage == 0 ? new Color(.25f,.60f,.25f,1)
            : stage == 1 ? new Color(.85f,.95f,1f,1) : Color.TEAL;
        for (float x = b.x + Math.max(0, (int)((camera.position.x - 430 - b.x) / 64)) * 64; x < Math.min(b.x + b.width, camera.position.x + 430); x += 64) {
            float width = Math.min(64, b.x + b.width - x);
            if (groundTopTex == white) rect(x, b.y + b.height - 8, width, 8, top);
            else batch.draw(groundTopTex, x, b.y + b.height - 8, width, 8,
                0f, 1f, width / 64f, 0f);
        }
    }
    private void draw() {
        ScreenUtils.clear(.04f, .07f, .13f, 1);
        viewport.apply(); batch.enableBlending(); batch.setProjectionMatrix(camera.combined); batch.begin();
        Texture bg = backgrounds[stage];
        if (bg != white) batch.draw(bg, camera.position.x - W / 2, camera.position.y - H / 2, W, H);
        // Wash out scenery only; foreground sprites and projectiles retain full contrast.
        rect(camera.position.x - W / 2, camera.position.y - H / 2, W, H,
            new Color(.90f, .95f, 1f, .82f));
        for (Rectangle b : blocks) drawTerrain(b);
        for (Panel p : panels) {
            image(p.timer > 0 ? panelOnTex : panelOffTex, p.floor, p.timer > 0 ? Color.CYAN : Color.GRAY, false);
            image(deviceTex, p.receiver, p.timer > 0 ? Color.CYAN : Color.ORANGE, false);
        }
        image(deviceTex, device, rush == Rush.DONE ? Color.GREEN : Color.ORANGE, false);
        if (stage == 0 && rush != Rush.DONE) image(gateTex, gate, Color.RED, false);
        if (stage == 1 && rush == Rush.DONE) image(bridgeTex, bridge, Color.LIGHT_GRAY, false);
        if (stage == 2) image(padTex, pad, rush == Rush.DONE ? Color.CYAN : Color.GRAY, false);
        if (rush == Rush.FIGHT || rush == Rush.WARNING) {
            rect(9790, 60, 10, 260, Color.ORANGE); rect(12000, 60, 10, 260, Color.ORANGE);
        }
        if (stage > 0) {
            image(deviceTex, gimmickReceiver, gimmickActive ? Color.CYAN : Color.ORANGE, false);
            if (stage == 1 && !gimmickActive) image(gateTex, gimmickGate, Color.RED, false);
            if (stage == 2 && gimmickActive) image(bridgeTex, gimmickBridge, Color.LIGHT_GRAY, false);
        }
        for (Rectangle orb : xpItems) image(xpTex, orb, Color.GOLD, false);
        image(exitTex, exit, Color.GOLD, false);
        for (Enemy e : enemies) {
            Texture t = e.type == 3 ? flyingTex : e.type == 0 ? patrolTex : e.type == 1 ? chargerTex : shooterTex;
            image(t, e.box, e.type == 0 ? Color.GREEN : e.type == 1 ? Color.ORANGE : Color.PURPLE,
                e.noticed ? player.x < e.box.x : e.direction < 0);
            if (e.windup > 0) rect(e.box.x, e.box.y + 48, 30, 5, Color.YELLOW);
            if (e.type == 1 && e.state == 1) rect(e.box.x, e.box.y + 46, 30, 5, Color.RED);
            rect(e.box.x, e.box.y + 42, e.box.width, 3, Color.DARK_GRAY);
            rect(e.box.x, e.box.y + 42, e.box.width * Math.max(0, e.hp) / (e.type == 1 ? 5f : 3f), 3, Color.RED);
        }
        if (bossAlive) {
            image(bossTex, boss, Color.PURPLE, false);

        }
        if (bossAlive && rush == Rush.DONE) {
            if (bossAreaWarning > 0) rect(bossArea.x, bossArea.y, bossArea.width, 6,
                new Color(1, .65f, 0, .8f));
            else if (bossAreaActive > 0) rect(bossArea.x, bossArea.y, bossArea.width,
                bossArea.height, new Color(1, .15f, .05f, .75f));
        }
        for (Shot s : shots) image(s.missile ? missileTex : bulletTex, s.box,
            s.hostile ? Color.RED : fever() ? Color.CYAN : Color.YELLOW, s.vx < 0);
        if (invincible <= 0 || ((int)(invincible * 14)) % 2 == 0)
            image(playerTex, player, fever() ? Color.CYAN : Color.WHITE, facing < 0);
        batch.end();
        uiViewport.apply(); batch.setProjectionMatrix(uiCamera.combined); batch.begin();
        font.setColor(Color.WHITE);
        font.draw(batch, "STAGE " + (stage + 1) + "   HP " + hp + "/" + maxHp + "   LEVEL " + level
            + "   XP " + xp + "/" + nextXp, 18, 580);
        font.draw(batch, "Arrows: Move  SPACE: Jump  Z: Fire  X: Dash  Shoot receiver: Rush  ESC: Pause", 18, 25);
        if (rush == Rush.IDLE && near(player, device, 100)) font.draw(batch, "SHOOT THE RECEIVER TO UNLOCK ROUTE", 230, 510);
        if (rush == Rush.WARNING) font.draw(batch, "WARNING - AUTOMATIC FEVER STARTING", 220, 470);
        if (rush == Rush.FIGHT) font.draw(batch, "FEVER!  Spawned: " + rushSpawned + "/" + rushTotal + "  Alive: " + enemies.size, 240, 530);
        if (chargeGrace > 0) font.draw(batch, "CHARGED: SPEED +40% / HIGH JUMP", 220, 510);
        if (player.x > 3440 && player.x < 4260 && panels.size > 0 && panels.first().timer <= 0)
            font.draw(batch, "SHOOT ORANGE RECEIVER - CHARGE FLOOR TO JUMP THE CLIFF", 120, 470);
        if (rush == Rush.DONE) font.draw(batch, "ROUTE UNLOCKED", 320, 550);
        if (pendingChoices > 0 && rush == Rush.FIGHT) font.draw(batch, "Pending upgrades: " + pendingChoices, 18, 550);
        if (bossAlive && rush == Rush.DONE) font.draw(batch, "BOSS HP: " + Math.max(0, bossHp)
            + " / 200  ALWAYS VULNERABLE", 280, 490);
        if (mode != Mode.PLAY) {
            rect(100, 170, 600, 260, new Color(0, 0, 0, .9f));
            if (mode == Mode.CHOOSE) {
                font.draw(batch, "LEVEL UP - CHOOSE ONE", 260, 390);
                for (int i = 0; i < 3; i++) font.draw(batch, (i + 1) + ": " + upgradeNames[options[i]], 240, 335 - i * 45);
            } else font.draw(batch, mode == Mode.PAUSE ? "PAUSED - ESC to resume"
                : mode == Mode.DEAD ? "DEFEATED - ENTER to retry stage"
                : "ALL STAGES CLEAR! ENTER to restart", 220, 315);
        }
        batch.end();
    }
    @Override public void resize(int width, int height) {
        viewport.update(width, height, false); uiViewport.update(width, height, true);
    }
    @Override public void dispose() {
        batch.dispose(); font.dispose();
        for (Texture t : owned) t.dispose();
    }
}
