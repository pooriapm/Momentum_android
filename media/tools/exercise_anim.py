"""
Exercise animation generator.

Every exercise in the library points at one movement family ("squat", "row", ...) and an equipment
variant ("dumbbell", "barbell", ...). Each family is a few key poses of one flat figure; this script
interpolates the poses, solves the skeleton for every other frame and writes a small Lottie file per
family and variant, so all animations share one style and the app downloads only the ones it shows.

    python media/tools/exercise_anim.py            # writes media/exercise/v1/anim/*.zip
    python media/tools/exercise_anim.py --preview  # also writes contact sheets (needs Pillow)

Pose angles are in degrees. Limb segments are absolute: 0 points straight down, 90 points forward
(the way the figure faces), 180 points up. The torso is measured from upright: 0 is standing tall,
90 is lying face down with the head forward, -90 is lying on the back with the head behind.
In the front view the "F" limbs are on the right of the picture and the "B" limbs mirror them.
"""

import json
import math
import os
import sys
import zipfile

W = H = 240
GROUND = 222
FPS = 30
STEP = 3  # a pose is solved every 3 frames; Lottie interpolates linearly in between

L_TORSO, L_NECK, HEAD_R = 54, 5, 12
L_UARM, L_FARM = 30, 28
L_THIGH, L_SHIN, L_FOOT = 38, 38, 11

# Flat palette: the figure in saffron (back limbs darker), equipment in ink, on the app's cream tile.
FRONT = "#E8962E"
BACK = "#B9701C"
SKIN = "#F2B356"
INK = "#3A3631"
PROP = "#CFC6B8"
FLOOR = "#E3DCD0"

W_TORSO, W_THIGH, W_SHIN, W_UARM, W_FARM = 19, 13, 11, 10, 9

DEFAULT = dict(
    t=0, aF1=14, aF2=10, aB1=-10, aB2=-8, lF1=4, lF2=0, lB1=-4, lB2=0, fF=90, fB=90,
    lift=0, dx=0, view="side", anchor="lowest", px=None, py=None,
)


def d(angle):
    a = math.radians(angle)
    return math.sin(a), math.cos(a)


def add(p, v, k=1.0):
    return p[0] + v[0] * k, p[1] + v[1] * k


def solve(pose):
    """Joint positions for one pose, before it is placed on the ground."""
    front = pose["view"] == "front"
    t = math.radians(pose["t"])
    up = (math.sin(t), -math.cos(t))
    side = (math.cos(t), math.sin(t))
    pelvis = (0.0, 0.0)
    neck = add(pelvis, up, L_TORSO)
    head = add(neck, up, L_NECK + HEAD_R)
    shoulder = add(neck, up, -5)

    def arm(key, sign):
        base = add(shoulder, side, 13 * sign) if front else shoulder
        mirror = -1 if (front and sign < 0) else 1
        u = d(pose[key + "1"]); u = (u[0] * mirror, u[1])
        f = d(pose[key + "2"]); f = (f[0] * mirror, f[1])
        elbow = add(base, u, L_UARM)
        hand = add(elbow, f, L_FARM)
        return [base, elbow, hand]

    def leg(key, foot, sign):
        base = add(pelvis, side, 8 * sign) if front else pelvis
        mirror = -1 if (front and sign < 0) else 1
        u = d(pose[key + "1"]); u = (u[0] * mirror, u[1])
        s = d(pose[key + "2"]); s = (s[0] * mirror, s[1])
        knee = add(base, u, L_THIGH)
        ankle = add(knee, s, L_SHIN)
        if front:
            toe = add(ankle, (sign * 1.0, 0.0), 6)
        else:
            toe = add(ankle, d(pose[foot]), L_FOOT)
        return [base, knee, ankle, toe]

    return {
        "torso": [pelvis, neck],
        "head": head,
        "armF": arm("aF", 1),
        "armB": arm("aB", -1),
        "legF": leg("lF", "fF", 1),
        "legB": leg("lB", "fB", -1),
    }


def body_points(j):
    pts = [j["head"]] + j["torso"] + j["armF"] + j["armB"] + j["legF"] + j["legB"]
    return pts


def place(j, pose, props_floor):
    """Moves the figure so its lowest point rests on the ground (or on a prop), then lifts it."""
    if pose["anchor"] == "fixed":
        ox, oy = pose["px"], pose["py"]
    else:
        lowest = max(p[1] for p in body_points(j))
        floor = props_floor if props_floor is not None else GROUND
        # Standing figures start with the pelvis at the centre; a prop scene places them with dx.
        ox, oy = W / 2, floor - lowest - 6
    oy -= pose["lift"]
    ox += pose["dx"]

    def mv(p):
        return (p[0] + ox, p[1] + oy)

    out = {}
    for k, v in j.items():
        out[k] = mv(v) if k == "head" else [mv(p) for p in v]
    return out


def shift_x(j, dx):
    out = {}
    for k, v in j.items():
        out[k] = (v[0] + dx, v[1]) if k == "head" else [(p[0] + dx, p[1]) for p in v]
    return out


def ease(x):
    return x * x * (3 - 2 * x)


def lerp_pose(a, b, x):
    out = dict(a)
    for k, v in a.items():
        if isinstance(v, (int, float)) and k in b and isinstance(b[k], (int, float)) and v is not None:
            out[k] = v + (b[k] - v) * x
    return out


class Family:
    def __init__(self, name, keys, view="side", props=(), floor=None, hold=6):
        """keys: list of (frames_to_next, pose overrides). The loop returns to the first pose."""
        self.name = name
        self.view = view
        self.keys = keys
        self.props = props
        self.floor = floor
        self.hold = hold

    def poses(self):
        full = []
        for frames, over in self.keys:
            p = dict(DEFAULT)
            p["view"] = self.view
            p.update(over)
            full.append((frames, p))
        return full

    def frames(self):
        """(frame number, placed joints) every STEP frames, looping back to the start."""
        poses = self.poses()
        out = []
        frame = 0
        for i, (length, pose) in enumerate(poses):
            nxt = poses[(i + 1) % len(poses)][1]
            moving = max(length - self.hold, 2)
            for f in range(0, length, STEP):
                x = 0.0 if f < self.hold else ease(min(1.0, (f - self.hold) / moving))
                p = lerp_pose(pose, nxt, x)
                out.append((frame + f, place(solve(p), p, self.floor)))
            frame += length
        # Figures that stand on the floor are centred once for the whole loop, so the motion stays visible.
        if poses[0][1]["anchor"] != "fixed" and not self.props:
            xs = [p[0] for _, j in out for p in body_points(j)]
            shift = W / 2 - (min(xs) + max(xs)) / 2
            out = [(f, shift_x(j, shift)) for f, j in out]
        out.append((frame, out[0][1]))
        return out, frame


# ---- equipment ------------------------------------------------------------------------------

def perp(a, b, length):
    dx, dy = b[0] - a[0], b[1] - a[1]
    n = math.hypot(dx, dy) or 1.0
    return (-dy / n * length, dx / n * length)


def gear(kind, j, view):
    """Equipment shapes for one frame: list of (name, kind, points or circle, width, color)."""
    out = []
    handF = j["armF"][2]
    handB = j["armB"][2]
    elbowF = j["armF"][1]
    elbowB = j["armB"][1]
    if kind == "dumbbell":
        for name, hand, elbow in (("dbB", handB, elbowB), ("dbF", handF, elbowF)):
            if view == "side" and name == "dbB":
                continue
            v = perp(elbow, hand, 9)
            a, b = add(hand, v, -1), add(hand, v, 1)
            out.append((name + "bar", "line", [a, b], 4, INK))
            out.append((name + "p1", "line", [add(a, v, -0.25), add(a, v, 0.25)], 10, INK))
            out.append((name + "p2", "line", [add(b, v, -0.25), add(b, v, 0.25)], 10, INK))
    elif kind == "barbell":
        if view == "front":
            v = (handF[0] - handB[0], handF[1] - handB[1])
            n = math.hypot(*v) or 1.0
            u = (v[0] / n, v[1] / n)
            a, b = add(handB, u, -34), add(handF, u, 34)
            out.append(("bar", "line", [a, b], 4, INK))
            out.append(("plateL", "line", [add(a, (u[1], -u[0]), -12), add(a, (u[1], -u[0]), 12)], 9, INK))
            out.append(("plateR", "line", [add(b, (u[1], -u[0]), -12), add(b, (u[1], -u[0]), 12)], 9, INK))
        else:
            out.append(("plate", "circle", (handF, 17), 0, INK))
            out.append(("hub", "circle", (handF, 5), 0, PROP))
    elif kind == "kettlebell":
        mid = ((handF[0] + handB[0]) / 2, (handF[1] + handB[1]) / 2) if view == "front" else handF
        v = perp(elbowF, handF, 1) if view == "side" else (0, 1)
        down = (handF[0] - elbowF[0], handF[1] - elbowF[1])
        n = math.hypot(*down) or 1.0
        down = (down[0] / n, down[1] / n)
        body = add(mid, down, 13)
        out.append(("kbHandle", "line", [add(mid, v, -5), add(mid, v, 5)], 4, INK))
        out.append(("kb", "circle", (body, 10), 0, INK))
    elif kind == "plate":
        out.append(("plate", "circle", (handF, 14), 0, INK))
        out.append(("hub", "circle", (handF, 4), 0, PROP))
    elif kind == "medball":
        out.append(("ball", "circle", (handF, 11), 0, INK))
    return out


def tethers(kind, j, anchors):
    """Bands, cables and TRX straps: a line from each hand to its anchor point."""
    out = []
    if kind not in ("band", "cable", "trx"):
        return out
    width = {"band": 3, "cable": 2, "trx": 3}[kind]
    color = {"band": "#4FA36C", "cable": INK, "trx": "#E0592A"}[kind]
    for i, hand in enumerate((j["armF"][2], j["armB"][2])):
        if i < len(anchors):
            out.append(("tether%d" % i, "line", [anchors[i], hand], width, color))
    return out


# ---- static props ---------------------------------------------------------------------------

def prop_shapes(props):
    out = [("floor", "line", [(16, GROUND), (W - 16, GROUND)], 3, FLOOR)]
    for p in props:
        kind = p[0]
        if kind == "bench":
            x0, x1, y = p[1], p[2], p[3]
            out.append(("benchTop", "line", [(x0, y), (x1, y)], 9, PROP))
            out.append(("benchLeg1", "line", [(x0 + 8, y), (x0 + 8, GROUND)], 5, PROP))
            out.append(("benchLeg2", "line", [(x1 - 8, y), (x1 - 8, GROUND)], 5, PROP))
        elif kind == "box":
            x0, x1, y = p[1], p[2], p[3]
            out.append(("box", "rect", (x0, y, x1, GROUND), 0, PROP))
        elif kind == "bar":
            x0, x1, y = p[1], p[2], p[3]
            out.append(("bar", "line", [(x0, y), (x1, y)], 5, INK))
            out.append(("post1", "line", [(x0, y), (x0, GROUND)], 4, PROP))
            out.append(("post2", "line", [(x1, y), (x1, GROUND)], 4, PROP))
        elif kind == "column":
            x, y = p[1], p[2]
            out.append(("column", "line", [(x, y), (x, GROUND)], 8, PROP))
        elif kind == "wall":
            x = p[1]
            out.append(("wall", "line", [(x, 40), (x, GROUND)], 6, PROP))
        elif kind == "mat":
            x0, x1 = p[1], p[2]
            out.append(("mat", "line", [(x0, GROUND - 3), (x1, GROUND - 3)], 6, "#EBC79A"))
        elif kind == "anchor":
            x, y = p[1], p[2]
            out.append(("anchor", "circle", ((x, y), 4), 0, INK))
        elif kind == "parallel":
            x0, x1, y = p[1], p[2], p[3]
            out.append(("rail", "line", [(x0, y), (x1, y)], 6, INK))
            out.append(("rail1", "line", [(x0 + 4, y), (x0 + 4, GROUND)], 4, PROP))
            out.append(("rail2", "line", [(x1 - 4, y), (x1 - 4, GROUND)], 4, PROP))
        elif kind == "seat":
            x0, x1, y = p[1], p[2], p[3]
            out.append(("seat", "line", [(x0, y), (x1, y)], 9, PROP))
            out.append(("seatLeg", "line", [((x0 + x1) / 2, y), ((x0 + x1) / 2, GROUND)], 6, PROP))
        elif kind == "backrest":
            x0, y0, x1, y1 = p[1], p[2], p[3], p[4]
            out.append(("backrest", "line", [(x0, y0), (x1, y1)], 9, PROP))
    return out


# ---- families ---------------------------------------------------------------------------------

def stand(**k):
    return k


ARMS_FWD = dict(aF1=85, aF2=85, aB1=80, aB2=80)
ARMS_DOWN = dict(aF1=14, aF2=10, aB1=-10, aB2=-8)
ARMS_CHEST = dict(aF1=30, aF2=150, aB1=25, aB2=150)
ARMS_RACK = dict(aF1=-30, aF2=165, aB1=-35, aB2=165)
SQUAT_DOWN = dict(t=38, lF1=95, lF2=-25, lB1=90, lB2=-25)


def families():
    F = {}
    # Squat: bodyweight with arms forward, goblet at the chest, barbell racked on the back.
    F["squat"] = Family("squat", [(30, stand(**ARMS_FWD)), (30, stand(**ARMS_FWD, **SQUAT_DOWN))])
    F["squat_goblet"] = Family("squat_goblet", [(30, stand(**ARMS_CHEST)), (30, stand(**ARMS_CHEST, **SQUAT_DOWN))])
    F["squat_rack"] = Family("squat_rack", [(30, stand(**ARMS_RACK)), (30, stand(**ARMS_RACK, **SQUAT_DOWN))])
    F["wall_sit"] = Family("wall_sit", [(40, stand(t=0, lF1=88, lF2=0, lB1=86, lB2=0, aF1=10, aF2=20, aB1=5, aB2=15, dx=-8)),
                                         (40, stand(t=0, lF1=88, lF2=0, lB1=86, lB2=0, aF1=12, aF2=24, aB1=6, aB2=18, dx=-8))],
                           props=[("wall", 74)])
    # Lunge: step forward, back knee towards the floor.
    F["lunge"] = Family("lunge", [
        (30, stand(**ARMS_DOWN, lF1=4, lF2=0, lB1=-4, lB2=0)),
        (30, stand(aF1=4, aF2=4, aB1=-4, aB2=-4, lF1=80, lF2=0, lB1=-30, lB2=-80, fB=170)),
    ])
    F["split_squat"] = Family("split_squat", [
        (30, stand(**ARMS_DOWN, lF1=30, lF2=0, lB1=-25, lB2=-10, fB=150)),
        (30, stand(**ARMS_DOWN, lF1=82, lF2=0, lB1=-30, lB2=-82, fB=170)),
    ])
    F["step_up"] = Family("step_up", [
        (30, stand(**ARMS_DOWN, lF1=70, lF2=-10, lB1=-2, lB2=0, dx=-22)),
        (30, stand(**ARMS_DOWN, lF1=4, lF2=0, lB1=-10, lB2=-40, dx=-6, lift=34)),
    ], props=[("box", 118, 186, 188)])
    # Hinge: hips back, flat back, weight close to the legs.
    F["hinge"] = Family("hinge", [
        (30, stand(**ARMS_DOWN)),
        (30, stand(t=72, aF1=0, aF2=0, aB1=0, aB2=0, lF1=-14, lF2=-6, lB1=-16, lB2=-6)),
    ])
    F["good_morning"] = Family("good_morning", [
        (30, stand(aF1=-40, aF2=170, aB1=-45, aB2=170)),
        (30, stand(t=70, aF1=40, aF2=200, aB1=35, aB2=200, lF1=-12, lF2=-6, lB1=-14, lB2=-6)),
    ])
    F["swing"] = Family("swing", [
        (24, stand(t=60, aF1=-20, aF2=-20, aB1=-24, aB2=-24, lF1=30, lF2=-15, lB1=26, lB2=-15)),
        (24, stand(t=0, aF1=90, aF2=90, aB1=86, aB2=86)),
    ], hold=2)
    # Floor press patterns.
    F["pushup"] = Family("pushup", [
        (28, stand(t=78, aF1=0, aF2=0, aB1=-2, aB2=-2, lF1=-78, lF2=-78, lB1=-80, lB2=-80, fF=180, fB=180)),
        (28, stand(t=86, aF1=60, aF2=-10, aB1=58, aB2=-12, lF1=-86, lF2=-86, lB1=-87, lB2=-87, fF=180, fB=180)),
    ])
    F["knee_pushup"] = Family("knee_pushup", [
        (28, stand(t=62, aF1=0, aF2=0, aB1=-2, aB2=-2, lF1=-62, lF2=-150, lB1=-64, lB2=-150, fF=200, fB=200)),
        (28, stand(t=76, aF1=58, aF2=-12, aB1=56, aB2=-14, lF1=-76, lF2=-155, lB1=-78, lB2=-155, fF=200, fB=200)),
    ])
    F["wall_pushup"] = Family("wall_pushup", [
        (28, stand(t=28, aF1=92, aF2=92, aB1=90, aB2=90, lF1=-24, lF2=-24, lB1=-26, lB2=-26, dx=-26)),
        (28, stand(t=36, aF1=130, aF2=60, aB1=128, aB2=58, lF1=-30, lF2=-30, lB1=-32, lB2=-32, dx=-18)),
    ], props=[("wall", 172)])
    F["plank"] = Family("plank", [
        (36, stand(t=84, aF1=0, aF2=88, aB1=-2, aB2=86, lF1=-84, lF2=-84, lB1=-86, lB2=-86, fF=180, fB=180)),
        (36, stand(t=82, aF1=0, aF2=88, aB1=-2, aB2=86, lF1=-82, lF2=-82, lB1=-84, lB2=-84, fF=180, fB=180, lift=3)),
    ], props=[("mat", 30, 210)])
    F["mountain_climber"] = Family("mountain_climber", [
        (16, stand(t=74, aF1=0, aF2=0, aB1=-2, aB2=-2, lF1=20, lF2=-40, lB1=-74, lB2=-74, fF=150, fB=180)),
        (16, stand(t=74, aF1=0, aF2=0, aB1=-2, aB2=-2, lF1=-74, lF2=-74, lB1=20, lB2=-40, fF=180, fB=150)),
    ], hold=1)
    F["burpee"] = Family("burpee", [
        (18, stand(**ARMS_DOWN)),
        (18, stand(t=40, aF1=10, aF2=0, aB1=8, aB2=0, lF1=110, lF2=-30, lB1=105, lB2=-30)),
        (20, stand(t=78, aF1=0, aF2=0, aB1=-2, aB2=-2, lF1=-78, lF2=-78, lB1=-80, lB2=-80, fF=180, fB=180)),
        (18, stand(t=40, aF1=10, aF2=0, aB1=8, aB2=0, lF1=110, lF2=-30, lB1=105, lB2=-30)),
        (20, stand(aF1=175, aF2=178, aB1=170, aB2=176, lift=26)),
    ], hold=2)
    # Bench patterns (lying on a bench, head to the left).
    bench = [("bench", 40, 160, 172)]
    lying = dict(t=-90, anchor="fixed", px=126, py=158, lF1=60, lF2=0, lB1=58, lB2=0, fF=90, fB=90)
    F["bench_press"] = Family("bench_press", [
        (28, stand(**lying, aF1=150, aF2=180, aB1=148, aB2=180)),
        (28, stand(**lying, aF1=95, aF2=215, aB1=93, aB2=213)),
    ], props=bench)
    F["chest_fly"] = Family("chest_fly", [
        (30, stand(**lying, aF1=178, aF2=180, aB1=176, aB2=180)),
        (30, stand(**lying, aF1=110, aF2=125, aB1=108, aB2=123)),
    ], props=bench)
    F["skull_crusher"] = Family("skull_crusher", [
        (28, stand(**lying, aF1=170, aF2=180, aB1=168, aB2=180)),
        (28, stand(**lying, aF1=170, aF2=250, aB1=168, aB2=248)),
    ], props=bench)
    F["pullover"] = Family("pullover", [
        (30, stand(**lying, aF1=178, aF2=178, aB1=176, aB2=176)),
        (30, stand(**lying, aF1=255, aF2=258, aB1=253, aB2=256)),
    ], props=bench)
    F["hip_thrust"] = Family("hip_thrust", [
        (28, stand(t=-60, anchor="fixed", px=118, py=196, lF1=60, lF2=-5, lB1=58, lB2=-5, aF1=40, aF2=40, aB1=38, aB2=38)),
        (28, stand(t=-92, anchor="fixed", px=126, py=160, lF1=92, lF2=0, lB1=90, lB2=0, aF1=40, aF2=40, aB1=38, aB2=38)),
    ], props=[("bench", 40, 84, 170)])
    # Floor patterns on the back.
    floor_back = dict(t=-90, anchor="fixed", px=130, py=208)
    F["glute_bridge"] = Family("glute_bridge", [
        (28, stand(**floor_back, lF1=150, lF2=20, lB1=148, lB2=20, aF1=-80, aF2=-80, aB1=-82, aB2=-82)),
        (28, stand(t=-72, anchor="fixed", px=132, py=184, lF1=100, lF2=0, lB1=98, lB2=0, aF1=-60, aF2=-80, aB1=-62, aB2=-82)),
    ], props=[("mat", 30, 210)])
    F["crunch"] = Family("crunch", [
        (28, stand(**floor_back, lF1=140, lF2=30, lB1=138, lB2=30, aF1=-120, aF2=150, aB1=-122, aB2=150)),
        (28, stand(t=-55, anchor="fixed", px=130, py=208, lF1=140, lF2=30, lB1=138, lB2=30, aF1=-60, aF2=200, aB1=-62, aB2=200)),
    ], props=[("mat", 30, 210)])
    F["situp"] = Family("situp", [
        (30, stand(**floor_back, lF1=140, lF2=30, lB1=138, lB2=30, aF1=-150, aF2=-150, aB1=-152, aB2=-152)),
        (30, stand(t=-10, anchor="fixed", px=130, py=208, lF1=140, lF2=30, lB1=138, lB2=30, aF1=70, aF2=80, aB1=68, aB2=78)),
    ], props=[("mat", 30, 210)])
    F["leg_raise"] = Family("leg_raise", [
        (30, stand(**floor_back, lF1=92, lF2=92, lB1=90, lB2=90, fF=180, fB=180, aF1=88, aF2=88, aB1=86, aB2=86)),
        (30, stand(**floor_back, lF1=175, lF2=175, lB1=173, lB2=173, fF=270, fB=270, aF1=88, aF2=88, aB1=86, aB2=86)),
    ], props=[("mat", 30, 210)])
    F["dead_bug"] = Family("dead_bug", [
        (26, stand(**floor_back, lF1=180, lF2=90, lB1=178, lB2=88, aF1=180, aF2=180, aB1=178, aB2=178)),
        (26, stand(**floor_back, lF1=120, lF2=108, lB1=178, lB2=88, aF1=180, aF2=180, aB1=250, aB2=250)),
    ], props=[("mat", 30, 210)])
    F["russian_twist"] = Family("russian_twist", [
        (20, stand(t=-35, anchor="fixed", px=120, py=204, lF1=130, lF2=40, lB1=128, lB2=40, aF1=70, aF2=110, aB1=40, aB2=110)),
        (20, stand(t=-35, anchor="fixed", px=120, py=204, lF1=130, lF2=40, lB1=128, lB2=40, aF1=40, aF2=110, aB1=70, aB2=110)),
    ], props=[("mat", 30, 210)], hold=2)
    # Quadruped.
    quad = dict(t=88, aF1=0, aF2=0, aB1=-2, aB2=-2, lF1=-2, lF2=-92, lB1=-4, lB2=-92, fF=180, fB=180)
    F["bird_dog"] = Family("bird_dog", [
        (30, stand(**quad)),
        (30, stand(**dict(quad, aF1=172, aF2=172, lB1=-88, lB2=-88, fB=180))),
    ], props=[("mat", 30, 210)])
    F["cat_cow"] = Family("cat_cow", [
        (30, stand(**dict(quad, t=82))),
        (30, stand(**dict(quad, t=96, lift=4))),
    ], props=[("mat", 30, 210)])
    # Pulls.
    F["row"] = Family("row", [
        (28, stand(t=62, aF1=0, aF2=0, aB1=-2, aB2=-2, lF1=20, lF2=-10, lB1=18, lB2=-10)),
        (28, stand(t=62, aF1=-60, aF2=40, aB1=-62, aB2=38, lF1=20, lF2=-10, lB1=18, lB2=-10)),
    ])
    seated = dict(t=-4, anchor="fixed", px=100, py=168, lF1=92, lF2=10, lB1=90, lB2=10)
    F["seated_row"] = Family("seated_row", [
        (28, stand(**seated, aF1=90, aF2=90, aB1=88, aB2=88)),
        (28, stand(**dict(seated, t=-12), aF1=-30, aF2=90, aB1=-32, aB2=88)),
    ], props=[("seat", 72, 118, 176), ("column", 206, 60), ("anchor", 206, 150)])
    F["pulldown"] = Family("pulldown", [
        (28, stand(view="front", t=0, anchor="fixed", px=120, py=170, aF1=160, aF2=172, aB1=160, aB2=172,
                   lF1=60, lF2=5, lB1=60, lB2=5)),
        (28, stand(view="front", t=0, anchor="fixed", px=120, py=170, aF1=110, aF2=200, aB1=110, aB2=200,
                   lF1=60, lF2=5, lB1=60, lB2=5)),
    ], view="front", props=[("seat", 92, 148, 178), ("anchor", 120, 30)])
    F["pullup"] = Family("pullup", [
        (30, stand(view="front", t=0, anchor="fixed", px=120, py=150, aF1=168, aF2=176, aB1=168, aB2=176,
                   lF1=4, lF2=10, lB1=4, lB2=10)),
        (30, stand(view="front", t=0, anchor="fixed", px=120, py=124, aF1=112, aF2=205, aB1=112, aB2=205,
                   lF1=4, lF2=10, lB1=4, lB2=10)),
    ], view="front", props=[("bar", 40, 200, 34)])
    # Leaning back from straps anchored up on the left, heels on the floor on the right.
    F["inverted_row"] = Family("inverted_row", [
        (28, stand(t=-50, anchor="fixed", px=134, py=170, aF1=207, aF2=207, aB1=205, aB2=205, lF1=50, lF2=50, lB1=48, lB2=48, fF=150, fB=150)),
        (28, stand(t=-32, anchor="fixed", px=130, py=164, aF1=150, aF2=230, aB1=148, aB2=228, lF1=56, lF2=56, lB1=54, lB2=54, fF=150, fB=150)),
    ])
    F["face_pull"] = Family("face_pull", [
        (28, stand(aF1=90, aF2=90, aB1=88, aB2=88)),
        (28, stand(aF1=100, aF2=200, aB1=98, aB2=198)),
    ], props=[("column", 214, 60), ("anchor", 214, 98)])
    F["pull_apart"] = Family("pull_apart", [
        (28, stand(view="front", aF1=80, aF2=80, aB1=80, aB2=80)),
        (28, stand(view="front", aF1=96, aF2=100, aB1=96, aB2=100)),
    ], view="front")
    # Presses and raises (front view).
    F["overhead_press"] = Family("overhead_press", [
        (28, stand(view="front", aF1=100, aF2=200, aB1=100, aB2=200)),
        (28, stand(view="front", aF1=170, aF2=176, aB1=170, aB2=176)),
    ], view="front")
    F["lateral_raise"] = Family("lateral_raise", [
        (28, stand(view="front", aF1=12, aF2=14, aB1=12, aB2=14)),
        (28, stand(view="front", aF1=86, aF2=90, aB1=86, aB2=90)),
    ], view="front")
    F["front_raise"] = Family("front_raise", [
        (28, stand(aF1=4, aF2=4, aB1=-4, aB2=-4)),
        (28, stand(aF1=90, aF2=92, aB1=86, aB2=88)),
    ])
    F["curl"] = Family("curl", [
        (26, stand(aF1=4, aF2=6, aB1=-4, aB2=-2)),
        (26, stand(aF1=6, aF2=150, aB1=-2, aB2=146)),
    ])
    F["triceps_overhead"] = Family("triceps_overhead", [
        (28, stand(aF1=172, aF2=178, aB1=170, aB2=176)),
        (28, stand(aF1=172, aF2=330, aB1=170, aB2=328)),
    ])
    F["pushdown"] = Family("pushdown", [
        (26, stand(t=8, aF1=8, aF2=100, aB1=6, aB2=98)),
        (26, stand(t=8, aF1=8, aF2=8, aB1=6, aB2=6)),
    ], props=[("column", 200, 40), ("anchor", 200, 52)])
    # Hands stay on the bars; the body lowers by bending the elbows.
    F["dip"] = Family("dip", [
        (28, stand(t=6, anchor="fixed", px=114, py=119, aF1=4, aF2=4, aB1=2, aB2=2, lF1=12, lF2=-70, lB1=10, lB2=-72, fF=60, fB=60)),
        (28, stand(t=14, anchor="fixed", px=118, py=139, aF1=-70, aF2=10, aB1=-72, aB2=8, lF1=12, lF2=-70, lB1=10, lB2=-72, fF=60, fB=60)),
    ], props=[("parallel", 70, 170, 128)])
    # Legs on machines.
    F["leg_press"] = Family("leg_press", [
        (30, stand(t=-55, anchor="fixed", px=92, py=176, lF1=150, lF2=30, lB1=148, lB2=30, aF1=40, aF2=40, aB1=38, aB2=38, fF=170, fB=170)),
        (30, stand(t=-55, anchor="fixed", px=92, py=176, lF1=112, lF2=108, lB1=110, lB2=106, aF1=40, aF2=40, aB1=38, aB2=38, fF=200, fB=200)),
    ], props=[("backrest", 50, 130, 96, 190), ("seat", 80, 112, 186)])
    F["leg_extension"] = Family("leg_extension", [
        (26, stand(t=-6, anchor="fixed", px=104, py=170, lF1=90, lF2=0, lB1=88, lB2=0, aF1=10, aF2=40, aB1=8, aB2=38)),
        (26, stand(t=-6, anchor="fixed", px=104, py=170, lF1=90, lF2=88, lB1=88, lB2=86, aF1=10, aF2=40, aB1=8, aB2=38, fF=180, fB=180)),
    ], props=[("seat", 78, 128, 178), ("backrest", 82, 120, 86, 176)])
    F["leg_curl"] = Family("leg_curl", [
        (26, stand(t=92, anchor="fixed", px=120, py=166, lF1=-92, lF2=-92, lB1=-90, lB2=-90, aF1=-10, aF2=-10, aB1=-12, aB2=-12, fF=180, fB=180)),
        (26, stand(t=92, anchor="fixed", px=120, py=166, lF1=-92, lF2=-200, lB1=-90, lB2=-198, aF1=-10, aF2=-10, aB1=-12, aB2=-12, fF=260, fB=260)),
    ], props=[("bench", 40, 200, 178)])
    F["calf_raise"] = Family("calf_raise", [
        (22, stand(**ARMS_DOWN)),
        (22, stand(**ARMS_DOWN, fF=150, fB=150, lift=8)),
    ])
    # Cardio.
    F["march"] = Family("march", [
        (16, stand(aF1=-30, aF2=40, aB1=30, aB2=70, lF1=80, lF2=-10, lB1=-2, lB2=0)),
        (16, stand(aF1=30, aF2=70, aB1=-30, aB2=40, lF1=-2, lF2=0, lB1=80, lB2=-10)),
    ], hold=1)
    F["run"] = Family("run", [
        (12, stand(t=10, aF1=-40, aF2=60, aB1=40, aB2=110, lF1=70, lF2=-20, lB1=-30, lB2=-90, lift=6)),
        (12, stand(t=10, aF1=40, aF2=110, aB1=-40, aB2=60, lF1=-30, lF2=-90, lB1=70, lB2=-20, lift=6)),
    ], hold=0)
    F["walk"] = Family("walk", [
        (20, stand(aF1=-20, aF2=-10, aB1=20, aB2=30, lF1=24, lF2=10, lB1=-24, lB2=-34)),
        (20, stand(aF1=20, aF2=30, aB1=-20, aB2=-10, lF1=-24, lF2=-34, lB1=24, lB2=10)),
    ], hold=0)
    F["carry"] = Family("carry", [
        (20, stand(aF1=4, aF2=4, aB1=-4, aB2=-4, lF1=24, lF2=10, lB1=-24, lB2=-34)),
        (20, stand(aF1=4, aF2=4, aB1=-4, aB2=-4, lF1=-24, lF2=-34, lB1=24, lB2=10)),
    ], hold=0)
    F["jumping_jack"] = Family("jumping_jack", [
        (14, stand(view="front", aF1=10, aF2=10, aB1=10, aB2=10, lF1=3, lF2=3, lB1=3, lB2=3)),
        (14, stand(view="front", aF1=160, aF2=170, aB1=160, aB2=170, lF1=24, lF2=24, lB1=24, lB2=24, lift=10)),
    ], view="front", hold=1)
    F["jump_rope"] = Family("jump_rope", [
        (10, stand(view="front", aF1=20, aF2=60, aB1=20, aB2=60, lF1=3, lF2=3, lB1=3, lB2=3)),
        (10, stand(view="front", aF1=24, aF2=80, aB1=24, aB2=80, lF1=3, lF2=6, lB1=3, lB2=6, lift=12)),
    ], view="front", hold=0)
    F["jump_squat"] = Family("jump_squat", [
        (18, stand(**ARMS_DOWN, t=38, lF1=95, lF2=-25, lB1=90, lB2=-25)),
        (18, stand(aF1=170, aF2=175, aB1=165, aB2=172, lift=28)),
    ], hold=2)
    F["box_jump"] = Family("box_jump", [
        (20, stand(t=40, aF1=-40, aF2=-40, aB1=-44, aB2=-44, lF1=95, lF2=-25, lB1=90, lB2=-25, dx=-40)),
        (20, stand(aF1=160, aF2=170, aB1=156, aB2=166, lF1=60, lF2=-60, lB1=56, lB2=-60, lift=56, dx=-6)),
        (20, stand(t=30, aF1=80, aF2=80, aB1=76, aB2=76, lF1=80, lF2=-20, lB1=76, lB2=-20, lift=50, dx=24)),
    ], props=[("box", 120, 196, 172)], hold=2)
    F["cycle"] = Family("cycle", [
        (15, stand(t=24, anchor="fixed", px=110, py=150, aF1=70, aF2=80, aB1=68, aB2=78, lF1=60, lF2=-10, lB1=110, lB2=40)),
        (15, stand(t=24, anchor="fixed", px=110, py=150, aF1=70, aF2=80, aB1=68, aB2=78, lF1=110, lF2=40, lB1=60, lB2=-10)),
    ], props=[("seat", 98, 122, 158), ("column", 110, 158), ("column", 172, 104)], hold=0)
    F["rower"] = Family("rower", [
        (24, stand(t=26, anchor="fixed", px=104, py=196, aF1=90, aF2=90, aB1=88, aB2=88, lF1=150, lF2=30, lB1=148, lB2=30, fF=150, fB=150)),
        (24, stand(t=-20, anchor="fixed", px=72, py=196, aF1=-20, aF2=90, aB1=-22, aB2=88, lF1=96, lF2=90, lB1=94, lB2=88, fF=180, fB=180)),
    ], props=[("seat", 40, 220, 206)], hold=2)
    F["battle_rope"] = Family("battle_rope", [
        (10, stand(t=20, aF1=60, aF2=70, aB1=40, aB2=50, lF1=40, lF2=-20, lB1=36, lB2=-20)),
        (10, stand(t=20, aF1=40, aF2=50, aB1=60, aB2=70, lF1=40, lF2=-20, lB1=36, lB2=-20)),
    ], props=[("anchor", 222, 210)], hold=0)
    # Mobility.
    F["stretch_reach"] = Family("stretch_reach", [
        (40, stand(view="front", aF1=170, aF2=176, aB1=170, aB2=176)),
        (40, stand(view="front", t=-14, aF1=196, aF2=200, aB1=150, aB2=160)),
        (40, stand(view="front", aF1=170, aF2=176, aB1=170, aB2=176)),
        (40, stand(view="front", t=14, aF1=150, aF2=160, aB1=196, aB2=200)),
    ], view="front", hold=10)
    F["forward_fold"] = Family("forward_fold", [
        (40, stand(aF1=170, aF2=176, aB1=166, aB2=172)),
        (40, stand(t=118, aF1=-6, aF2=0, aB1=-8, aB2=-2)),
    ], hold=10)
    F["hip_opener"] = Family("hip_opener", [
        (40, stand(t=0, aF1=10, aF2=10, aB1=-6, aB2=-6, lF1=80, lF2=0, lB1=-40, lB2=-100, fB=180)),
        (40, stand(t=-10, aF1=170, aF2=176, aB1=166, aB2=172, lF1=86, lF2=0, lB1=-50, lB2=-100, fB=180, lift=-4)),
    ], hold=10)
    F["sit_to_stand"] = Family("sit_to_stand", [
        (30, stand(**ARMS_FWD, t=30, lF1=92, lF2=-6, lB1=90, lB2=-6, dx=-4)),
        (30, stand(**ARMS_FWD, dx=10)),
    ], props=[("seat", 96, 134, 178)])
    F["chest_press"] = Family("chest_press", [
        (26, stand(t=6, aF1=-40, aF2=90, aB1=-42, aB2=88, lF1=20, lF2=0, lB1=-14, lB2=-6)),
        (26, stand(t=10, aF1=88, aF2=90, aB1=86, aB2=88, lF1=20, lF2=0, lB1=-14, lB2=-6)),
    ], props=[("column", 34, 60), ("anchor", 34, 116)])
    F["upright_row"] = Family("upright_row", [
        (26, stand(view="front", aF1=10, aF2=-10, aB1=10, aB2=-10)),
        (26, stand(view="front", aF1=110, aF2=-40, aB1=110, aB2=-40)),
    ], view="front")
    F["thruster"] = Family("thruster", [
        (24, stand(**ARMS_RACK, **SQUAT_DOWN)),
        (24, stand(aF1=172, aF2=178, aB1=168, aB2=176)),
    ], hold=3)
    F["superman"] = Family("superman", [
        (30, stand(t=90, anchor="fixed", px=96, py=204, aF1=92, aF2=92, aB1=90, aB2=90, lF1=-90, lF2=-90, lB1=-90, lB2=-90, fF=180, fB=180)),
        (30, stand(t=82, anchor="fixed", px=96, py=204, aF1=104, aF2=106, aB1=102, aB2=104, lF1=-100, lF2=-102, lB1=-98, lB2=-100, fF=190, fB=190)),
    ], props=[("mat", 30, 210)])
    F["hanging_leg_raise"] = Family("hanging_leg_raise", [
        (30, stand(t=0, anchor="fixed", px=120, py=148, aF1=180, aF2=180, aB1=178, aB2=178, lF1=4, lF2=4, lB1=2, lB2=2)),
        (30, stand(t=-6, anchor="fixed", px=122, py=146, aF1=180, aF2=180, aB1=178, aB2=178, lF1=92, lF2=92, lB1=90, lB2=90, fF=180, fB=180)),
    ], props=[("bar", 40, 200, 34)])
    F["donkey_kick"] = Family("donkey_kick", [
        (26, stand(**dict(quad, lB1=-4, lB2=-92))),
        (26, stand(**dict(quad, lB1=-120, lB2=-160, fB=-120))),
    ], props=[("mat", 30, 210)])
    F["trx_press"] = Family("trx_press", [
        (28, stand(t=34, aF1=88, aF2=90, aB1=86, aB2=88, lF1=-34, lF2=-34, lB1=-36, lB2=-36, dx=-10)),
        (28, stand(t=44, aF1=40, aF2=130, aB1=38, aB2=128, lF1=-44, lF2=-44, lB1=-46, lB2=-46, dx=-2)),
    ], props=[("anchor", 70, 20)])
    F["down_dog"] = Family("down_dog", [
        (40, stand(t=128, aF1=34, aF2=34, aB1=32, aB2=32, lF1=-36, lF2=-36, lB1=-38, lB2=-38, fF=110, fB=110)),
        (40, stand(t=124, aF1=38, aF2=38, aB1=36, aB2=36, lF1=-32, lF2=-32, lB1=-34, lB2=-34, fF=110, fB=110, lift=3)),
    ], props=[("mat", 30, 210)], hold=10)
    F["cobra"] = Family("cobra", [
        (36, stand(t=90, anchor="fixed", px=100, py=204, aF1=60, aF2=-30, aB1=58, aB2=-32, lF1=-90, lF2=-90, lB1=-90, lB2=-90, fF=180, fB=180)),
        (36, stand(t=50, anchor="fixed", px=100, py=204, aF1=4, aF2=0, aB1=2, aB2=-2, lF1=-90, lF2=-90, lB1=-90, lB2=-90, fF=180, fB=180)),
    ], props=[("mat", 30, 210)], hold=8)
    F["boxing"] = Family("boxing", [
        (10, stand(t=8, aF1=90, aF2=90, aB1=40, aB2=150, lF1=24, lF2=6, lB1=-20, lB2=-6, lift=2)),
        (10, stand(t=8, aF1=40, aF2=150, aB1=90, aB2=90, lF1=24, lF2=6, lB1=-20, lB2=-6)),
    ], hold=1)
    F["thoracic_rotation"] = Family("thoracic_rotation", [
        (36, stand(**quad)),
        (36, stand(**dict(quad, aF1=150, aF2=150))),
    ], props=[("mat", 30, 210)], hold=8)
    return F


# Equipment variants: which families can show which equipment, and band/TRX anchor points.
VARIANTS = {
    "squat": ["bodyweight", "trx"],
    "squat_goblet": ["kettlebell", "dumbbell", "plate", "medball"],
    "squat_rack": ["barbell"],
    "wall_sit": ["bodyweight"],
    "lunge": ["bodyweight", "dumbbell"],
    "split_squat": ["bodyweight", "dumbbell"],
    "step_up": ["bodyweight", "dumbbell"],
    "hinge": ["bodyweight", "dumbbell", "barbell", "kettlebell"],
    "good_morning": ["bodyweight"],
    "swing": ["kettlebell"],
    "pushup": ["bodyweight"],
    "knee_pushup": ["bodyweight"],
    "wall_pushup": ["bodyweight"],
    "plank": ["bodyweight"],
    "mountain_climber": ["bodyweight"],
    "burpee": ["bodyweight"],
    "bench_press": ["barbell", "dumbbell"],
    "chest_fly": ["dumbbell"],
    "skull_crusher": ["dumbbell"],
    "pullover": ["dumbbell"],
    "hip_thrust": ["bodyweight", "barbell"],
    "glute_bridge": ["bodyweight"],
    "crunch": ["bodyweight"],
    "situp": ["bodyweight"],
    "leg_raise": ["bodyweight"],
    "dead_bug": ["bodyweight"],
    "russian_twist": ["bodyweight", "medball"],
    "bird_dog": ["bodyweight"],
    "cat_cow": ["bodyweight"],
    "row": ["dumbbell", "barbell", "band"],
    "seated_row": ["cable"],
    "pulldown": ["cable"],
    "pullup": ["bodyweight"],
    "inverted_row": ["trx"],
    "face_pull": ["cable"],
    "pull_apart": ["band"],
    "overhead_press": ["dumbbell", "barbell"],
    "lateral_raise": ["dumbbell", "band"],
    "front_raise": ["dumbbell", "plate"],
    "curl": ["dumbbell", "barbell", "band"],
    "triceps_overhead": ["dumbbell"],
    "pushdown": ["cable"],
    "dip": ["bodyweight"],
    "leg_press": ["machine"],
    "leg_extension": ["machine"],
    "leg_curl": ["machine"],
    "calf_raise": ["bodyweight", "dumbbell"],
    "march": ["bodyweight"],
    "run": ["bodyweight"],
    "walk": ["bodyweight"],
    "carry": ["dumbbell", "kettlebell"],
    "jumping_jack": ["bodyweight"],
    "jump_rope": ["bodyweight"],
    "jump_squat": ["bodyweight"],
    "box_jump": ["bodyweight"],
    "cycle": ["machine"],
    "rower": ["machine"],
    "battle_rope": ["rope"],
    "stretch_reach": ["bodyweight"],
    "forward_fold": ["bodyweight"],
    "hip_opener": ["bodyweight"],
    "thoracic_rotation": ["bodyweight"],
    "sit_to_stand": ["bodyweight"],
    "chest_press": ["band", "cable"],
    "upright_row": ["dumbbell", "barbell"],
    "thruster": ["barbell", "dumbbell", "medball"],
    "superman": ["bodyweight"],
    "hanging_leg_raise": ["bodyweight"],
    "donkey_kick": ["bodyweight"],
    "trx_press": ["trx"],
    "down_dog": ["bodyweight"],
    "cobra": ["bodyweight"],
    "boxing": ["bodyweight"],
}

ANCHORS = {
    ("row", "band"): [(150, 212), (150, 212)],
    ("seated_row", "cable"): [(206, 150), (206, 150)],
    ("pulldown", "cable"): [(120, 30), (120, 30)],
    ("inverted_row", "trx"): [(40, 22), (40, 22)],
    ("face_pull", "cable"): [(214, 98), (214, 98)],
    ("pushdown", "cable"): [(200, 52), (200, 52)],
    ("pull_apart", "band"): [],
    ("lateral_raise", "band"): [(110, 214), (130, 214)],
    ("curl", "band"): [(126, 214), (126, 214)],
    ("battle_rope", "rope"): [(222, 210), (222, 210)],
    ("squat", "trx"): [(226, 26), (226, 26)],
    ("chest_press", "band"): [(34, 116), (34, 116)],
    ("chest_press", "cable"): [(34, 116), (34, 116)],
    ("trx_press", "trx"): [(70, 20), (70, 20)],
}


def tether_kind(variant):
    return {"band": "band", "cable": "cable", "trx": "trx", "rope": "trx"}.get(variant)


def shapes_for(fam, variant, j):
    shapes = []
    front = fam.view == "front"
    order_side = [("legB", BACK), ("armB", BACK)]
    order_front = [("legB", FRONT), ("legF", FRONT)]
    for name, color in (order_front if front else order_side):
        w = (W_THIGH, W_SHIN, W_SHIN) if name.startswith("leg") else (W_UARM, W_FARM)
        shapes.append((name, "limb", j[name], w, color))
    shapes.append(("torso", "line", j["torso"], W_TORSO, FRONT))
    shapes.append(("head", "circle", (j["head"], HEAD_R), 0, SKIN))
    if front:
        shapes.append(("armB", "limb", j["armB"], (W_UARM, W_FARM), FRONT))
        shapes.append(("armF", "limb", j["armF"], (W_UARM, W_FARM), FRONT))
    else:
        shapes.append(("legF", "limb", j["legF"], (W_THIGH, W_SHIN, W_SHIN), FRONT))
        shapes.append(("armF", "limb", j["armF"], (W_UARM, W_FARM), FRONT))
    kind = tether_kind(variant)
    if kind:
        shapes += tethers(kind, j, ANCHORS.get((fam.name, variant), []))
    shapes += gear(variant, j, fam.view)
    if variant == "band" and fam.name == "pull_apart":
        shapes.append(("band", "line", [j["armB"][2], j["armF"][2]], 3, "#4FA36C"))
    return shapes


# ---- Lottie writer ------------------------------------------------------------------------------

def rgba(hex_color):
    h = hex_color.lstrip("#")
    return [round(int(h[i:i + 2], 16) / 255, 4) for i in (0, 2, 4)] + [1]


def r1(v):
    return round(v, 1)


def path_value(points):
    return {"c": False, "v": [[r1(x), r1(y)] for x, y in points],
            "i": [[0, 0] for _ in points], "o": [[0, 0] for _ in points]}


LINEAR = {"i": {"x": [1], "y": [1]}, "o": {"x": [0], "y": [0]}}
LINEAR2 = {"i": {"x": [1, 1], "y": [1, 1]}, "o": {"x": [0, 0], "y": [0, 0]}}


def animated(values, frames, two_d=False):
    """A Lottie property: static when every frame has the same value, keyframed otherwise."""
    if all(v == values[0] for v in values):
        return {"a": 0, "k": values[0]}
    ks = []
    for i, (f, v) in enumerate(zip(frames, values)):
        if i < len(values) - 1 and v == values[i + 1] and i > 0 and v == values[i - 1]:
            continue
        k = {"t": f, "s": v if isinstance(v, list) and not isinstance(v, dict) else [v]}
        if isinstance(v, dict):
            k["s"] = [v]
        k.update(LINEAR2 if two_d else LINEAR)
        ks.append(k)
    return {"a": 1, "k": ks}


def stroke(color, width):
    return {"ty": "st", "c": {"a": 0, "k": rgba(color)}, "o": {"a": 0, "k": 100},
            "w": {"a": 0, "k": width}, "lc": 2, "lj": 2, "ml": 4}


def fill(color):
    return {"ty": "fl", "c": {"a": 0, "k": rgba(color)}, "o": {"a": 0, "k": 100}, "r": 1}


TRANSFORM = {"ty": "tr", "p": {"a": 0, "k": [0, 0]}, "a": {"a": 0, "k": [0, 0]},
             "s": {"a": 0, "k": [100, 100]}, "r": {"a": 0, "k": 0}, "o": {"a": 0, "k": 100}}


def group(items):
    return {"ty": "gr", "it": items + [TRANSFORM]}


def layer(index, name, groups, total):
    return {"ddd": 0, "ind": index, "ty": 4, "nm": name, "sr": 1,
            "ks": {"o": {"a": 0, "k": 100}, "r": {"a": 0, "k": 0}, "p": {"a": 0, "k": [0, 0, 0]},
                   "a": {"a": 0, "k": [0, 0, 0]}, "s": {"a": 0, "k": [100, 100, 100]}},
            "ao": 0, "shapes": groups, "ip": 0, "op": total, "st": 0, "bm": 0}


def shape_groups(kind, series, width, color, frames):
    if kind == "line":
        return [group([{"ty": "sh", "ks": animated([path_value(p) for p in series], frames)}, stroke(color, width)])]
    if kind == "limb":
        # Each segment gets its own width (thigh thicker than shin), drawn as separate strokes.
        n = len(series[0])
        out = []
        for s in range(n - 1):
            seg = [[pts[s], pts[s + 1]] for pts in series]
            w = width[min(s, len(width) - 1)]
            out.append(group([{"ty": "sh", "ks": animated([path_value(p) for p in seg], frames)}, stroke(color, w)]))
        return out
    if kind == "circle":
        centers = [[r1(c[0][0]), r1(c[0][1])] for c in series]
        radius = series[0][1]
        return [group([{"ty": "el", "p": animated(centers, frames, two_d=True), "s": {"a": 0, "k": [radius * 2, radius * 2]}},
                       fill(color)])]
    if kind == "rect":
        x0, y0, x1, y1 = series[0]
        return [group([{"ty": "rc", "p": {"a": 0, "k": [r1((x0 + x1) / 2), r1((y0 + y1) / 2)]},
                        "s": {"a": 0, "k": [r1(x1 - x0), r1(y1 - y0)]}, "r": {"a": 0, "k": 4}}, fill(color)])]
    raise ValueError(kind)


def lottie(fam, variant):
    samples, total = fam.frames()
    frames = [f for f, _ in samples]
    props = prop_shapes(fam.props)
    per_frame = [shapes_for(fam, variant, j) for _, j in samples]
    names = [s[0] for s in per_frame[0]]
    layers = []
    # Lottie draws the first layer on top, so the list is reversed.
    drawn = [(p[0], p[1], [p[2]] * len(frames), p[3], p[4]) for p in props]
    for idx, name in enumerate(names):
        kind, width, color = per_frame[0][idx][1], per_frame[0][idx][3], per_frame[0][idx][4]
        drawn.append((name, kind, [pf[idx][2] for pf in per_frame], width, color))
    for i, (name, kind, series, width, color) in enumerate(reversed(drawn)):
        layers.append(layer(i + 1, name, shape_groups(kind, series, width, color, frames), total))
    return {"v": "5.7.4", "fr": FPS, "ip": 0, "op": total, "w": W, "h": H, "nm": "%s-%s" % (fam.name, variant),
            "ddd": 0, "assets": [], "layers": layers}


# ---- preview -----------------------------------------------------------------------------------

def preview(fams, path):
    from PIL import Image, ImageDraw  # noqa: F401  (draw_cells draws with what this creates)

    cells = []
    for name, fam in fams.items():
        for variant in VARIANTS[name]:
            cells.append((fam, variant))
    cols = 4
    tile = 150
    per_sheet = 8
    scale = tile / W
    for start in range(0, len(cells), per_sheet):
        chunk = cells[start:start + per_sheet]
        sheet = Image.new("RGB", (cols * tile, len(chunk) * tile), "#FBF6EE")
        draw = ImageDraw.Draw(sheet)
        draw_cells(chunk, draw, cols, tile, scale)
        sheet.save(path.replace(".png", "_%d.png" % (start // per_sheet + 1)))


def draw_cells(cells, draw, cols, tile, scale):
    for r, (fam, variant) in enumerate(cells):
        samples, total = fam.frames()
        picks = [samples[int(i * (len(samples) - 1) / (cols - 1))] for i in range(cols)]
        for c, (_, j) in enumerate(picks):
            ox, oy = c * tile, r * tile

            def P(p):
                return (ox + p[0] * scale, oy + p[1] * scale)

            for shape in prop_shapes(fam.props) + shapes_for(fam, variant, j):
                name, kind, data, width, color = shape
                if kind in ("line", "limb"):
                    widths = width if isinstance(width, tuple) else (width,)
                    for s in range(len(data) - 1):
                        w = widths[min(s, len(widths) - 1)] * scale
                        a, b = P(data[s]), P(data[s + 1])
                        draw.line([a, b], fill=color, width=max(1, int(w)))
                        for q in (a, b):
                            draw.ellipse([q[0] - w / 2, q[1] - w / 2, q[0] + w / 2, q[1] + w / 2], fill=color)
                elif kind == "circle":
                    (cx, cy), rad = data
                    q = P((cx, cy))
                    draw.ellipse([q[0] - rad * scale, q[1] - rad * scale, q[0] + rad * scale, q[1] + rad * scale], fill=color)
                elif kind == "rect":
                    x0, y0, x1, y1 = data
                    draw.rectangle([P((x0, y0)), P((x1, y1))], fill=color)
            draw.text((ox + 4, oy + 2), "%s/%s" % (fam.name, variant), fill="#3A3631")


def main():
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    out_dir = os.path.join(root, "exercise", "v1", "anim")
    os.makedirs(out_dir, exist_ok=True)
    fams = families()
    total_bytes = 0
    count = 0
    for name, fam in fams.items():
        for variant in VARIANTS[name]:
            data = json.dumps(lottie(fam, variant), separators=(",", ":"), ensure_ascii=False)
            # Zipped (Lottie reads zips directly): about 2 KB each instead of 30 KB of JSON.
            target = os.path.join(out_dir, "%s-%s.zip" % (name, variant))
            info = zipfile.ZipInfo("animation.json", date_time=(2026, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            with zipfile.ZipFile(target, "w") as z:
                z.writestr(info, data)
            total_bytes += os.path.getsize(target)
            count += 1
    print("%d animations, %.0f KB total, %.1f KB average" % (count, total_bytes / 1024, total_bytes / 1024 / count))
    if "--preview" in sys.argv:
        target = sys.argv[sys.argv.index("--preview") + 1] if len(sys.argv) > sys.argv.index("--preview") + 1 else "preview.png"
        preview(fams, target)
        print("preview:", target)


if __name__ == "__main__":
    main()
