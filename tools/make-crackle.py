import math, random, struct, array, os

SR = 44100
DUR = 1.5
N = int(SR * DUR)
rng = random.Random(20261101)

brown = [0.0] * N
last = 0.0
for i in range(N):
    white = rng.uniform(-1, 1)
    last = (last + 0.02 * white) / 1.02
    brown[i] = last * 3.5

env = []
for i in range(N):
    t = i / SR
    a = min(1.0, t / 0.008)
    d = math.exp(-t * 3.2)
    env.append(a * d)

pops = [0.0] * N
pop_times = [0.02, 0.09, 0.19, 0.33, 0.5, 0.72]
for pt in pop_times:
    start = int(pt * SR)
    for j in range(int(0.004 * SR)):
        idx = start + j
        if 0 <= idx < N:
            pops[idx] += rng.uniform(-1, 1) * math.exp(-j / (0.0012 * SR))

signal = [0.0] * N
for i in range(N):
    s = brown[i] * 0.9 + rng.uniform(-1, 1) * 0.25 + pops[i] * 0.8
    signal[i] = s * env[i] * 0.5

peak = max(abs(s) for s in signal)
gain = (0.55 / peak) if peak > 0 else 1.0

left = [max(-1.0, min(1.0, s * gain)) for s in signal]
right = [max(-1.0, min(1.0, signal[(i - 40) % N] * gain * 0.95)) for i in range(N)]

pcm = array.array('h')
for l, r in zip(left, right):
    pcm.append(int(l * 32767))
    pcm.append(int(r * 32767))

out = r'C:\Users\30335\Desktop\wds\vue-demo\public\media\electric-crackle.wav'
data_size = N * 4
with open(out, 'wb') as f:
    f.write(b'RIFF')
    f.write(struct.pack('<I', 36 + data_size))
    f.write(b'WAVE')
    f.write(b'fmt ')
    f.write(struct.pack('<IHHIIHH', 16, 1, 2, SR, SR * 4, 4, 16))
    f.write(b'data')
    f.write(struct.pack('<I', data_size))
    pcm.tofile(f)

print('OK', os.path.getsize(out), 'bytes')
