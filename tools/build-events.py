import struct
from pathlib import Path
from keystone import Ks,KS_ARCH_ARM64,KS_MODE_LITTLE_ENDIAN
# AArch64 syscall-only helper. No libraries, no su. Parent death releases evdev.
asm="""ldr x20,[sp,#16]
ldr x21,[sp,#24]
mov x0,#1
mov x1,#15
mov x2,#0
mov x3,#0
mov x4,#0
mov x8,#167
svc #0
mov x8,#173
svc #0
cmp x0,#1
b.le exit
mov x0,#-100
mov x1,x20
mov x2,#0
mov x3,#0
mov x8,#56
svc #0
mov x19,x0
cmp x0,#0
b.lt header
ldrb w1,[x21]
cmp w1,#49
b.ne opened
mov x0,x19
mov x1,#0x4590
movk x1,#0x4004,lsl #16
mov x2,#1
mov x8,#29
svc #0
b header
opened:
mov x0,#0
header:
mov x22,x0
mov x1,#0x2000
movk x1,#0x40,lsl #16
str x0,[x1]
mov x0,#1
mov x2,#8
mov x8,#64
svc #0
cmp x22,#0
b.lt exit
loop:
mov x0,x19
mov x1,#0x2000
movk x1,#0x40,lsl #16
mov x2,#24
mov x8,#63
svc #0
cmp x0,#24
b.ne exit
mov x0,#1
mov x1,#0x2000
movk x1,#0x40,lsl #16
mov x2,#24
mov x8,#64
svc #0
cmp x0,#24
b.eq loop
exit:
mov x0,#0
mov x8,#93
svc #0"""
code=bytes(Ks(KS_ARCH_ARM64,KS_MODE_LITTLE_ENDIAN).asm(asm)[0]);b=bytearray(0x3000)
b[:64]=struct.pack('<16sHHIQQQIHHHHHH',b'\x7fELF'+bytes([2,1,1,0])+bytes(8),2,183,1,0x401000,64,0,0,64,56,1,0,0,0)
b[64:120]=struct.pack('<IIQQQQQQ',1,7,0,0x400000,0x400000,len(b),len(b),4096)
b[0x1000:0x1000+len(code)]=code
out=Path(__file__).parent/'tvbuttons-events';out.write_bytes(b);print(out)
