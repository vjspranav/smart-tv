
/private/tmp/flamingo-picture-20261101/libtpq.so:	file format elf32-littlearm

Disassembly of section .text:

000f5370 <TvPqManagerService::savePictureMode(unsigned int, int, TPQSDK_PICTURE_MODE_PRIORITY_T)>:
   f5370: e92d4800     	push	{r11, lr}
   f5374: e1a0b00d     	mov	r11, sp
   f5378: e24dd058     	sub	sp, sp, #88
   f537c: e50b0008     	str	r0, [r11, #-0x8]
   f5380: e50b100c     	str	r1, [r11, #-0xc]
   f5384: e50b2010     	str	r2, [r11, #-0x10]
   f5388: e50b3014     	str	r3, [r11, #-0x14]
   f538c: e51b0008     	ldr	r0, [r11, #-0x8]
   f5390: e51b1010     	ldr	r1, [r11, #-0x10]
   f5394: e51b2014     	ldr	r2, [r11, #-0x14]
   f5398: ebfd45aa     	bl	0x46a48 <_ZN18TvPqManagerService25checkPictureModeShouldSetEi30TPQSDK_PICTURE_MODE_PRIORITY_T@plt> @ imm = #-0xae958
   f539c: e3100001     	tst	r0, #1
   f53a0: 1a000003     	bne	0xf53b4 <TvPqManagerService::savePictureMode(unsigned int, int, TPQSDK_PICTURE_MODE_PRIORITY_T)+0x44> @ imm = #0xc
   f53a4: e3000000     	movw	r0, #0x0
   f53a8: e2000001     	and	r0, r0, #1
   f53ac: e54b0001     	strb	r0, [r11, #-0x1]
   f53b0: ea0000b6     	b	0xf5690 <TvPqManagerService::savePictureMode(unsigned int, int, TPQSDK_PICTURE_MODE_PRIORITY_T)+0x320> @ imm = #0x2d8
   f53b4: e3a00000     	mov	r0, #0
   f53b8: e54b0015     	strb	r0, [r11, #-0x15]
   f53bc: e58d0014     	str	r0, [sp, #0x14]
   f53c0: ebfd344b     	bl	0x424f4 <_ZN9StatusMgr11GetInstanceEv@plt> @ imm = #-0xb2ed4
   f53c4: e24b101c     	sub	r1, r11, #28
   f53c8: ebfd3215     	bl	0x41c24 <_ZN9StatusMgr16getVideoDeviceIdEP28PQBASE_TVPUINT_DEVICE_TYPE_T@plt> @ imm = #-0xb37ac
   f53cc: e51b101c     	ldr	r1, [r11, #-0x1c]
   f53d0: e50b1020     	str	r1, [r11, #-0x20]
   f53d4: e58d0010     	str	r0, [sp, #0x10]
   f53d8: ebfd3445     	bl	0x424f4 <_ZN9StatusMgr11GetInstanceEv@plt> @ imm = #-0xb2eec
   f53dc: e24b1028     	sub	r1, r11, #40
   f53e0: ebfd3f3b     	bl	0x450d4 <_ZN9StatusMgr32getVideoDynamicrangeCallbackInfoEP29PQBASE_VIDEO_DYNAMIC_RANGE_ST@plt> @ imm = #-0xb0314
   f53e4: e58d000c     	str	r0, [sp, #0xc]
   f53e8: ebfd397b     	bl	0x439dc <_ZN9ConfigMgr11GetInstanceEv@plt> @ imm = #-0xb1a14
   f53ec: e51b1010     	ldr	r1, [r11, #-0x10]
   f53f0: ebfd4900     	bl	0x477f8 <_ZN9ConfigMgr20getPictureTypeByModeEi@plt> @ imm = #-0xadc00
   f53f4: e58d002c     	str	r0, [sp, #0x2c]
   f53f8: e59d302c     	ldr	r3, [sp, #0x2c]
   f53fc: e51b0024     	ldr	r0, [r11, #-0x24]
   f5400: e1a0100d     	mov	r1, sp
   f5404: e5810000     	str	r0, [r1]
   f5408: e59f1290     	ldr	r1, [pc, #0x290]        @ 0xf56a0 <TvPqManagerService::savePictureMode(unsigned int, int, TPQSDK_PICTURE_MODE_PRIORITY_T)+0x330>
   f540c: e08f1001     	add	r1, pc, r1
   f5410: e59f228c     	ldr	r2, [pc, #0x28c]        @ 0xf56a4 <TvPqManagerService::savePictureMode(unsigned int, int, TPQSDK_PICTURE_MODE_PRIORITY_T)+0x334>
   f5414: e08f2002     	add	r2, pc, r2
   f5418: e3a00004     	mov	r0, #4
   f541c: ebfd38e7     	bl	0x437c0 <__android_log_print@plt> @ imm = #-0xb1c64
   f5420: e3a01001     	mov	r1, #1
   f5424: e58d1028     	str	r1, [sp, #0x28]
   f5428: e59d1014     	ldr	r1, [sp, #0x14]
   f542c: e58d1024     	str	r1, [sp, #0x24]
   f5430: e51b2014     	ldr	r2, [r11, #-0x14]
   f5434: e3520003     	cmp	r2, #3
   f5438: e58d0008     	str	r0, [sp, #0x8]
   f543c: e58d2004     	str	r2, [sp, #0x4]
   f5440: 8a000015     	bhi	0xf549c <TvPqManagerService::savePictureMode(unsigned int, int, TPQSDK_PICTURE_MODE_PRIORITY_T)+0x12c> @ imm = #0x54
   f5444: e28f0008     	add	r0, pc, #8
   f5448: e59d1004     	ldr	r1, [sp, #0x4]
   f544c: e7902101     	ldr	r2, [r0, r1, lsl #2]
   f5450: e080f002     	add	pc, r0, r2
   f5454: 00000010     	andeq	r0, r0, r0, lsl r0
   f5458: 0000001c     	andeq	r0, r0, r12, lsl r0
   f545c: 00000030     	andeq	r0, r0, r0, lsr r0
   f5460: 0000003c     	andeq	r0, r0, r12, lsr r0
   f5464: e3000000     	movw	r0, #0x0
   f5468: e58d0028     	str	r0, [sp, #0x28]
   f546c: ea00000b     	b	0xf54a0 <TvPqManagerService::savePictureMode(unsigned int, int, TPQSDK_PICTURE_MODE_PRIORITY_T)+0x130> @ imm = #0x2c
   f5470: e30000ff     	movw	r0, #0xff
   f5474: e50b0010     	str	r0, [r11, #-0x10]
   f5478: e3000000     	movw	r0, #0x0
   f547c: e58d0028     	str	r0, [sp, #0x28]
   f5480: ea000006     	b	0xf54a0 <TvPqManagerService::savePictureMode(unsigned int, int, TPQSDK_PICTURE_MODE_PRIORITY_T)+0x130> @ imm = #0x18
   f5484: e3000001     	movw	r0, #0x1
   f5488: e58d0028     	str	r0, [sp, #0x28]
   f548c: ea000003     	b	0xf54a0 <TvPqManagerService::savePictureMode(unsigned int, int, TPQSDK_PICTURE_MODE_PRIORITY_T)+0x130> @ imm = #0xc
   f5490: e3000002     	movw	r0, #0x2
   f5494: e58d0028     	str	r0, [sp, #0x28]
   f5498: ea000000     	b	0xf54a0 <TvPqManagerService::savePictureMode(unsigned int, int, TPQSDK_PICTURE_MODE_PRIORITY_T)+0x130> @ imm = #0x0
   f549c: eaffffff     	b	0xf54a0 <TvPqManagerService::savePictureMode(unsigned int, int, TPQSDK_PICTURE_MODE_PRIORITY_T)+0x130> @ imm = #-0x4
   f54a0: e51b0014     	ldr	r0, [r11, #-0x14]
   f54a4: e3500001     	cmp	r0, #1
   f54a8: 0a000002     	beq	0xf54b8 <TvPqManagerService::savePictureMode(unsigned int, int, TPQSDK_PICTURE_MODE_PRIORITY_T)+0x148> @ imm = #0x8
   f54ac: e51b0014     	ldr	r0, [r11, #-0x14]
   f54b0: e3500000     	cmp	r0, #0
   f54b4: 1a000041     	bne	0xf55c0 <TvPqManagerService::savePictureMode(unsigned int, int, TPQSDK_PICTURE_MODE_PRIORITY_T)+0x250> @ imm = #0x104
   f54b8: e3000000     	movw	r0, #0x0
   f54bc: e58d0020     	str	r0, [sp, #0x20]
   f54c0: e59d0020     	ldr	r0, [sp, #0x20]
   f54c4: e350001e     	cmp	r0, #30
   f54c8: aa00003b     	bge	0xf55bc <TvPqManagerService::savePictureMode(unsigned int, int, TPQSDK_PICTURE_MODE_PRIORITY_T)+0x24c> @ imm = #0xec
   f54cc: e59d002c     	ldr	r0, [sp, #0x2c]
   f54d0: e3500005     	cmp	r0, #5
   f54d4: 1a000008     	bne	0xf54fc <TvPqManagerService::savePictureMode(unsigned int, int, TPQSDK_PICTURE_MODE_PRIORITY_T)+0x18c> @ imm = #0x20
   f54d8: e59d0020     	ldr	r0, [sp, #0x20]
   f54dc: e350000f     	cmp	r0, #15
   f54e0: aa000000     	bge	0xf54e8 <TvPqManagerService::savePictureMode(unsigned int, int, TPQSDK_PICTURE_MODE_PRIORITY_T)+0x178> @ imm = #0x0
   f54e4: ea000030     	b	0xf55ac <TvPqManagerService::savePictureMode(unsigned int, int, TPQSDK_PICTURE_MODE_PRIORITY_T)+0x23c> @ imm = #0xc0
   f54e8: e59d0020     	ldr	r0, [sp, #0x20]
   f54ec: e3500012     	cmp	r0, #18
   f54f0: da000000     	ble	0xf54f8 <TvPqManagerService::savePictureMode(unsigned int, int, TPQSDK_PICTURE_MODE_PRIORITY_T)+0x188> @ imm = #0x0
   f54f4: ea000030     	b	0xf55bc <TvPqManagerService::savePictureMode(unsigned int, int, TPQSDK_PICTURE_MODE_PRIORITY_T)+0x24c> @ imm = #0xc0
   f54f8: eaffffff     	b	0xf54fc <TvPqManagerService::savePictureMode(unsigned int, int, TPQSDK_PICTURE_MODE_PRIORITY_T)+0x18c> @ imm = #-0x4
   f54fc: e3000000     	movw	r0, #0x0
   f5500: e58d001c     	str	r0, [sp, #0x1c]
   f5504: e59d001c     	ldr	r0, [sp, #0x1c]
   f5508: e3500007     	cmp	r0, #7
   f550c: aa000025     	bge	0xf55a8 <TvPqManagerService::savePictureMode(unsigned int, int, TPQSDK_PICTURE_MODE_PRIORITY_T)+0x238> @ imm = #0x94
   f5510: e51b0014     	ldr	r0, [r11, #-0x14]
   f5514: e3500000     	cmp	r0, #0
   f5518: 1a000014     	bne	0xf5570 <TvPqManagerService::savePictureMode(unsigned int, int, TPQSDK_PICTURE_MODE_PRIORITY_T)+0x200> @ imm = #0x50
   f551c: ebfd392e     	bl	0x439dc <_ZN9ConfigMgr11GetInstanceEv@plt> @ imm = #-0xb1b48
   f5520: e59d101c     	ldr	r1, [sp, #0x1c]
   f5524: e59d202c     	ldr	r2, [sp, #0x2c]
   f5528: ebfd3ca6     	bl	0x447c8 <_ZN9ConfigMgr20getPictureModeByTypeE27PQBASE_DYNAMIC_RANGE_TYPE_T27PQBASE_PIC_MODE_NAME_TYPE_T@plt> @ imm = #-0xb0d68
   f552c: e58d0024     	str	r0, [sp, #0x24]
   f5530: e59d0024     	ldr	r0, [sp, #0x24]
   f5534: e3500000     	cmp	r0, #0
   f5538: ba00000b     	blt	0xf556c <TvPqManagerService::savePictureMode(unsigned int, int, TPQSDK_PICTURE_MODE_PRIORITY_T)+0x1fc> @ imm = #0x2c
   f553c: e59d0024     	ldr	r0, [sp, #0x24]
   f5540: e350003d     	cmp	r0, #61
   f5544: aa000008     	bge	0xf556c <TvPqManagerService::savePictureMode(unsigned int, int, TPQSDK_PICTURE_MODE_PRIORITY_T)+0x1fc> @ imm = #0x20
   f5548: ebfd33e9     	bl	0x424f4 <_ZN9StatusMgr11GetInstanceEv@plt> @ imm = #-0xb305c
   f554c: e59d1020     	ldr	r1, [sp, #0x20]
   f5550: e59d201c     	ldr	r2, [sp, #0x1c]
   f5554: e59d3024     	ldr	r3, [sp, #0x24]
   f5558: e59de028     	ldr	lr, [sp, #0x28]
   f555c: e58de000     	str	lr, [sp]
   f5560: ebfd3350     	bl	0x422a8 <_ZN9StatusMgr14dataSetPicModeE19PQBASE_CODEC_TYPE_T27PQBASE_DYNAMIC_RANGE_TYPE_T22PQBASE_PIC_MODE_TYPE_Ti@plt> @ imm = #-0xb32c0
   f5564: e2000001     	and	r0, r0, #1
   f5568: e54b0015     	strb	r0, [r11, #-0x15]
   f556c: ea000008     	b	0xf5594 <TvPqManagerService::savePictureMode(unsigned int, int, TPQSDK_PICTURE_MODE_PRIORITY_T)+0x224> @ imm = #0x20
   f5570: ebfd33df     	bl	0x424f4 <_ZN9StatusMgr11GetInstanceEv@plt> @ imm = #-0xb3084
   f5574: e59d1020     	ldr	r1, [sp, #0x20]
   f5578: e59d201c     	ldr	r2, [sp, #0x1c]
   f557c: e51b3010     	ldr	r3, [r11, #-0x10]
   f5580: e59de028     	ldr	lr, [sp, #0x28]
   f5584: e58de000     	str	lr, [sp]
   f5588: ebfd3346     	bl	0x422a8 <_ZN9StatusMgr14dataSetPicModeE19PQBASE_CODEC_TYPE_T27PQBASE_DYNAMIC_RANGE_TYPE_T22PQBASE_PIC_MODE_TYPE_Ti@plt> @ imm = #-0xb32e8
   f558c: e2000001     	and	r0, r0, #1
   f5590: e54b0015     	strb	r0, [r11, #-0x15]
   f5594: eaffffff     	b	0xf5598 <TvPqManagerService::savePictureMode(unsigned int, int, TPQSDK_PICTURE_MODE_PRIORITY_T)+0x228> @ imm = #-0x4
   f5598: e59d001c     	ldr	r0, [sp, #0x1c]
   f559c: e2800001     	add	r0, r0, #1
   f55a0: e58d001c     	str	r0, [sp, #0x1c]
   f55a4: eaffffd6     	b	0xf5504 <TvPqManagerService::savePictureMode(unsigned int, int, TPQSDK_PICTURE_MODE_PRIORITY_T)+0x194> @ imm = #-0xa8
   f55a8: eaffffff     	b	0xf55ac <TvPqManagerService::savePictureMode(unsigned int, int, TPQSDK_PICTURE_MODE_PRIORITY_T)+0x23c> @ imm = #-0x4
   f55ac: e59d0020     	ldr	r0, [sp, #0x20]
   f55b0: e2800001     	add	r0, r0, #1
   f55b4: e58d0020     	str	r0, [sp, #0x20]
   f55b8: eaffffc0     	b	0xf54c0 <TvPqManagerService::savePictureMode(unsigned int, int, TPQSDK_PICTURE_MODE_PRIORITY_T)+0x150> @ imm = #-0x100
   f55bc: ea000030     	b	0xf5684 <TvPqManagerService::savePictureMode(unsigned int, int, TPQSDK_PICTURE_MODE_PRIORITY_T)+0x314> @ imm = #0xc0
   f55c0: e51b0024     	ldr	r0, [r11, #-0x24]
   f55c4: e3500005     	cmp	r0, #5
   f55c8: 1a000009     	bne	0xf55f4 <TvPqManagerService::savePictureMode(unsigned int, int, TPQSDK_PICTURE_MODE_PRIORITY_T)+0x284> @ imm = #0x24
   f55cc: ebfd33c8     	bl	0x424f4 <_ZN9StatusMgr11GetInstanceEv@plt> @ imm = #-0xb30e0
   f55d0: e51b1020     	ldr	r1, [r11, #-0x20]
   f55d4: e51b3010     	ldr	r3, [r11, #-0x10]
   f55d8: e59de028     	ldr	lr, [sp, #0x28]
   f55dc: e3002005     	movw	r2, #0x5
   f55e0: e58de000     	str	lr, [sp]
   f55e4: ebfd332f     	bl	0x422a8 <_ZN9StatusMgr14dataSetPicModeE19PQBASE_CODEC_TYPE_T27PQBASE_DYNAMIC_RANGE_TYPE_T22PQBASE_PIC_MODE_TYPE_Ti@plt> @ imm = #-0xb3344
   f55e8: e2000001     	and	r0, r0, #1
   f55ec: e54b0015     	strb	r0, [r11, #-0x15]
   f55f0: ea000022     	b	0xf5680 <TvPqManagerService::savePictureMode(unsigned int, int, TPQSDK_PICTURE_MODE_PRIORITY_T)+0x310> @ imm = #0x88
   f55f4: e3000000     	movw	r0, #0x0
   f55f8: e58d0018     	str	r0, [sp, #0x18]
   f55fc: e59d0018     	ldr	r0, [sp, #0x18]
   f5600: e3500007     	cmp	r0, #7
   f5604: aa00001c     	bge	0xf567c <TvPqManagerService::savePictureMode(unsigned int, int, TPQSDK_PICTURE_MODE_PRIORITY_T)+0x30c> @ imm = #0x70
   f5608: e59d0018     	ldr	r0, [sp, #0x18]
   f560c: e3500005     	cmp	r0, #5
   f5610: 1a000000     	bne	0xf5618 <TvPqManagerService::savePictureMode(unsigned int, int, TPQSDK_PICTURE_MODE_PRIORITY_T)+0x2a8> @ imm = #0x0
   f5614: ea000014     	b	0xf566c <TvPqManagerService::savePictureMode(unsigned int, int, TPQSDK_PICTURE_MODE_PRIORITY_T)+0x2fc> @ imm = #0x50
   f5618: ebfd38ef     	bl	0x439dc <_ZN9ConfigMgr11GetInstanceEv@plt> @ imm = #-0xb1c44
   f561c: e59d1018     	ldr	r1, [sp, #0x18]
   f5620: e59d202c     	ldr	r2, [sp, #0x2c]
   f5624: ebfd3c67     	bl	0x447c8 <_ZN9ConfigMgr20getPictureModeByTypeE27PQBASE_DYNAMIC_RANGE_TYPE_T27PQBASE_PIC_MODE_NAME_TYPE_T@plt> @ imm = #-0xb0e64
   f5628: e50b0010     	str	r0, [r11, #-0x10]
   f562c: e51b0010     	ldr	r0, [r11, #-0x10]
   f5630: e3500000     	cmp	r0, #0
   f5634: ba00000b     	blt	0xf5668 <TvPqManagerService::savePictureMode(unsigned int, int, TPQSDK_PICTURE_MODE_PRIORITY_T)+0x2f8> @ imm = #0x2c
   f5638: e51b0010     	ldr	r0, [r11, #-0x10]
   f563c: e350003d     	cmp	r0, #61
   f5640: aa000008     	bge	0xf5668 <TvPqManagerService::savePictureMode(unsigned int, int, TPQSDK_PICTURE_MODE_PRIORITY_T)+0x2f8> @ imm = #0x20
   f5644: ebfd33aa     	bl	0x424f4 <_ZN9StatusMgr11GetInstanceEv@plt> @ imm = #-0xb3158
   f5648: e51b1020     	ldr	r1, [r11, #-0x20]
   f564c: e59d2018     	ldr	r2, [sp, #0x18]
   f5650: e51b3010     	ldr	r3, [r11, #-0x10]
   f5654: e59de028     	ldr	lr, [sp, #0x28]
   f5658: e58de000     	str	lr, [sp]
   f565c: ebfd3311     	bl	0x422a8 <_ZN9StatusMgr14dataSetPicModeE19PQBASE_CODEC_TYPE_T27PQBASE_DYNAMIC_RANGE_TYPE_T22PQBASE_PIC_MODE_TYPE_Ti@plt> @ imm = #-0xb33bc
   f5660: e2000001     	and	r0, r0, #1
   f5664: e54b0015     	strb	r0, [r11, #-0x15]
   f5668: eaffffff     	b	0xf566c <TvPqManagerService::savePictureMode(unsigned int, int, TPQSDK_PICTURE_MODE_PRIORITY_T)+0x2fc> @ imm = #-0x4
   f566c: e59d0018     	ldr	r0, [sp, #0x18]
   f5670: e2800001     	add	r0, r0, #1
   f5674: e58d0018     	str	r0, [sp, #0x18]
   f5678: eaffffdf     	b	0xf55fc <TvPqManagerService::savePictureMode(unsigned int, int, TPQSDK_PICTURE_MODE_PRIORITY_T)+0x28c> @ imm = #-0x84
   f567c: eaffffff     	b	0xf5680 <TvPqManagerService::savePictureMode(unsigned int, int, TPQSDK_PICTURE_MODE_PRIORITY_T)+0x310> @ imm = #-0x4
   f5680: eaffffff     	b	0xf5684 <TvPqManagerService::savePictureMode(unsigned int, int, TPQSDK_PICTURE_MODE_PRIORITY_T)+0x314> @ imm = #-0x4
   f5684: e55b0015     	ldrb	r0, [r11, #-0x15]
   f5688: e2000001     	and	r0, r0, #1
   f568c: e54b0001     	strb	r0, [r11, #-0x1]
   f5690: e55b0001     	ldrb	r0, [r11, #-0x1]
   f5694: e2000001     	and	r0, r0, #1
   f5698: e1a0d00b     	mov	sp, r11
   f569c: e8bd8800     	pop	{r11, pc}
   f56a0: 000a6e63     	andeq	r6, r10, r3, ror #28
   f56a4: 000a6e67     	andeq	r6, r10, r7, ror #28

000f56a8 <TvPqManagerService::savePictureMode(unsigned int, TPQSDK_VIDEO_APPLY_T, int, TPQSDK_PICTURE_MODE_PRIORITY_T)>:
   f56a8: e92d4800     	push	{r11, lr}
   f56ac: e1a0b00d     	mov	r11, sp
   f56b0: e24dd058     	sub	sp, sp, #88
   f56b4: e59bc008     	ldr	r12, [r11, #0x8]
   f56b8: e50b0008     	str	r0, [r11, #-0x8]
   f56bc: e50b100c     	str	r1, [r11, #-0xc]
   f56c0: e50b2010     	str	r2, [r11, #-0x10]
   f56c4: e50b3014     	str	r3, [r11, #-0x14]
   f56c8: e51b0008     	ldr	r0, [r11, #-0x8]
   f56cc: e51b1014     	ldr	r1, [r11, #-0x14]
   f56d0: e59b2008     	ldr	r2, [r11, #0x8]
   f56d4: e58dc01c     	str	r12, [sp, #0x1c]
   f56d8: ebfd44da     	bl	0x46a48 <_ZN18TvPqManagerService25checkPictureModeShouldSetEi30TPQSDK_PICTURE_MODE_PRIORITY_T@plt> @ imm = #-0xaec98
   f56dc: e3100001     	tst	r0, #1
   f56e0: 1a000003     	bne	0xf56f4 <TvPqManagerService::savePictureMode(unsigned int, TPQSDK_VIDEO_APPLY_T, int, TPQSDK_PICTURE_MODE_PRIORITY_T)+0x4c> @ imm = #0xc
   f56e4: e3000000     	movw	r0, #0x0
   f56e8: e2000001     	and	r0, r0, #1
   f56ec: e54b0001     	strb	r0, [r11, #-0x1]
   f56f0: ea0000cf     	b	0xf5a34 <TvPqManagerService::savePictureMode(unsigned int, TPQSDK_VIDEO_APPLY_T, int, TPQSDK_PICTURE_MODE_PRIORITY_T)+0x38c> @ imm = #0x33c
   f56f4: e3a00000     	mov	r0, #0
   f56f8: e54b0015     	strb	r0, [r11, #-0x15]
   f56fc: ebfd337c     	bl	0x424f4 <_ZN9StatusMgr11GetInstanceEv@plt> @ imm = #-0xb3210
   f5700: e24b101c     	sub	r1, r11, #28
   f5704: ebfd3146     	bl	0x41c24 <_ZN9StatusMgr16getVideoDeviceIdEP28PQBASE_TVPUINT_DEVICE_TYPE_T@plt> @ imm = #-0xb3ae8
   f5708: e51b101c     	ldr	r1, [r11, #-0x1c]
   f570c: e50b1020     	str	r1, [r11, #-0x20]
   f5710: e58d0018     	str	r0, [sp, #0x18]
   f5714: ebfd3376     	bl	0x424f4 <_ZN9StatusMgr11GetInstanceEv@plt> @ imm = #-0xb3228
   f5718: e24b1028     	sub	r1, r11, #40
   f571c: ebfd3e6c     	bl	0x450d4 <_ZN9StatusMgr32getVideoDynamicrangeCallbackInfoEP29PQBASE_VIDEO_DYNAMIC_RANGE_ST@plt> @ imm = #-0xb0650
   f5720: e58d0014     	str	r0, [sp, #0x14]
   f5724: ebfd38ac     	bl	0x439dc <_ZN9ConfigMgr11GetInstanceEv@plt> @ imm = #-0xb1d50
   f5728: e51b1014     	ldr	r1, [r11, #-0x14]
   f572c: ebfd4831     	bl	0x477f8 <_ZN9ConfigMgr20getPictureTypeByModeEi@plt> @ imm = #-0xadf3c
   f5730: e58d002c     	str	r0, [sp, #0x2c]
   f5734: e59d302c     	ldr	r3, [sp, #0x2c]
   f5738: e51b0024     	ldr	r0, [r11, #-0x24]
   f573c: e1a0100d     	mov	r1, sp
   f5740: e5810000     	str	r0, [r1]
   f5744: e59f1308     	ldr	r1, [pc, #0x308]        @ 0xf5a54 <TvPqManagerService::savePictureMode(unsigned int, TPQSDK_VIDEO_APPLY_T, int, TPQSDK_PICTURE_MODE_PRIORITY_T)+0x3ac>
   f5748: e08f1001     	add	r1, pc, r1
   f574c: e59f2304     	ldr	r2, [pc, #0x304]        @ 0xf5a58 <TvPqManagerService::savePictureMode(unsigned int, TPQSDK_VIDEO_APPLY_T, int, TPQSDK_PICTURE_MODE_PRIORITY_T)+0x3b0>
   f5750: e08f2002     	add	r2, pc, r2
   f5754: e3a00004     	mov	r0, #4
   f5758: ebfd3818     	bl	0x437c0 <__android_log_print@plt> @ imm = #-0xb1fa0
   f575c: e3a01001     	mov	r1, #1
   f5760: e58d1028     	str	r1, [sp, #0x28]
   f5764: e59b1008     	ldr	r1, [r11, #0x8]
   f5768: e3510003     	cmp	r1, #3
   f576c: e58d0010     	str	r0, [sp, #0x10]
   f5770: e58d100c     	str	r1, [sp, #0xc]
   f5774: 8a000015     	bhi	0xf57d0 <TvPqManagerService::savePictureMode(unsigned int, TPQSDK_VIDEO_APPLY_T, int, TPQSDK_PICTURE_MODE_PRIORITY_T)+0x128> @ imm = #0x54
   f5778: e28f0008     	add	r0, pc, #8
   f577c: e59d100c     	ldr	r1, [sp, #0xc]
   f5780: e7902101     	ldr	r2, [r0, r1, lsl #2]
   f5784: e080f002     	add	pc, r0, r2
   f5788: 00000010     	andeq	r0, r0, r0, lsl r0
   f578c: 0000001c     	andeq	r0, r0, r12, lsl r0
   f5790: 00000030     	andeq	r0, r0, r0, lsr r0
   f5794: 0000003c     	andeq	r0, r0, r12, lsr r0
   f5798: e3000000     	movw	r0, #0x0
   f579c: e58d0028     	str	r0, [sp, #0x28]
   f57a0: ea00000b     	b	0xf57d4 <TvPqManagerService::savePictureMode(unsigned int, TPQSDK_VIDEO_APPLY_T, int, TPQSDK_PICTURE_MODE_PRIORITY_T)+0x12c> @ imm = #0x2c
   f57a4: e30000ff     	movw	r0, #0xff
   f57a8: e50b0014     	str	r0, [r11, #-0x14]
   f57ac: e3000000     	movw	r0, #0x0
   f57b0: e58d0028     	str	r0, [sp, #0x28]
   f57b4: ea000006     	b	0xf57d4 <TvPqManagerService::savePictureMode(unsigned int, TPQSDK_VIDEO_APPLY_T, int, TPQSDK_PICTURE_MODE_PRIORITY_T)+0x12c> @ imm = #0x18
   f57b8: e3000001     	movw	r0, #0x1
   f57bc: e58d0028     	str	r0, [sp, #0x28]
   f57c0: ea000003     	b	0xf57d4 <TvPqManagerService::savePictureMode(unsigned int, TPQSDK_VIDEO_APPLY_T, int, TPQSDK_PICTURE_MODE_PRIORITY_T)+0x12c> @ imm = #0xc
   f57c4: e3000002     	movw	r0, #0x2
   f57c8: e58d0028     	str	r0, [sp, #0x28]
   f57cc: ea000000     	b	0xf57d4 <TvPqManagerService::savePictureMode(unsigned int, TPQSDK_VIDEO_APPLY_T, int, TPQSDK_PICTURE_MODE_PRIORITY_T)+0x12c> @ imm = #0x0
   f57d0: eaffffff     	b	0xf57d4 <TvPqManagerService::savePictureMode(unsigned int, TPQSDK_VIDEO_APPLY_T, int, TPQSDK_PICTURE_MODE_PRIORITY_T)+0x12c> @ imm = #-0x4
   f57d4: e51b0010     	ldr	r0, [r11, #-0x10]
   f57d8: e3500001     	cmp	r0, #1
   f57dc: 1a000042     	bne	0xf58ec <TvPqManagerService::savePictureMode(unsigned int, TPQSDK_VIDEO_APPLY_T, int, TPQSDK_PICTURE_MODE_PRIORITY_T)+0x244> @ imm = #0x108
   f57e0: e59d0028     	ldr	r0, [sp, #0x28]
   f57e4: e3500001     	cmp	r0, #1
   f57e8: 1a00003f     	bne	0xf58ec <TvPqManagerService::savePictureMode(unsigned int, TPQSDK_VIDEO_APPLY_T, int, TPQSDK_PICTURE_MODE_PRIORITY_T)+0x244> @ imm = #0xfc
   f57ec: e59d002c     	ldr	r0, [sp, #0x2c]
   f57f0: e350000c     	cmp	r0, #12
   f57f4: 0a00003c     	beq	0xf58ec <TvPqManagerService::savePictureMode(unsigned int, TPQSDK_VIDEO_APPLY_T, int, TPQSDK_PICTURE_MODE_PRIORITY_T)+0x244> @ imm = #0xf0
   f57f8: e59d002c     	ldr	r0, [sp, #0x2c]
   f57fc: e3500005     	cmp	r0, #5
   f5800: ba000002     	blt	0xf5810 <TvPqManagerService::savePictureMode(unsigned int, TPQSDK_VIDEO_APPLY_T, int, TPQSDK_PICTURE_MODE_PRIORITY_T)+0x168> @ imm = #0x8
   f5804: e59d002c     	ldr	r0, [sp, #0x2c]
   f5808: e3500008     	cmp	r0, #8
   f580c: da000036     	ble	0xf58ec <TvPqManagerService::savePictureMode(unsigned int, TPQSDK_VIDEO_APPLY_T, int, TPQSDK_PICTURE_MODE_PRIORITY_T)+0x244> @ imm = #0xd8
   f5810: e59d002c     	ldr	r0, [sp, #0x2c]
   f5814: e350000e     	cmp	r0, #14
   f5818: ba000002     	blt	0xf5828 <TvPqManagerService::savePictureMode(unsigned int, TPQSDK_VIDEO_APPLY_T, int, TPQSDK_PICTURE_MODE_PRIORITY_T)+0x180> @ imm = #0x8
   f581c: e59d002c     	ldr	r0, [sp, #0x2c]
   f5820: e3500010     	cmp	r0, #16
   f5824: da000030     	ble	0xf58ec <TvPqManagerService::savePictureMode(unsigned int, TPQSDK_VIDEO_APPLY_T, int, TPQSDK_PICTURE_MODE_PRIORITY_T)+0x244> @ imm = #0xc0
   f5828: e51b0024     	ldr	r0, [r11, #-0x24]
   f582c: e3500005     	cmp	r0, #5
   f5830: 1a000009     	bne	0xf585c <TvPqManagerService::savePictureMode(unsigned int, TPQSDK_VIDEO_APPLY_T, int, TPQSDK_PICTURE_MODE_PRIORITY_T)+0x1b4> @ imm = #0x24
   f5834: ebfd332e     	bl	0x424f4 <_ZN9StatusMgr11GetInstanceEv@plt> @ imm = #-0xb3348
   f5838: e51b1020     	ldr	r1, [r11, #-0x20]
   f583c: e51b3014     	ldr	r3, [r11, #-0x14]
   f5840: e59de028     	ldr	lr, [sp, #0x28]
   f5844: e3002005     	movw	r2, #0x5
   f5848: e58de000     	str	lr, [sp]
   f584c: ebfd35c5     	bl	0x42f68 <_ZN9StatusMgr22dataSetApplyAllPicModeE19PQBASE_CODEC_TYPE_T27PQBASE_DYNAMIC_RANGE_TYPE_T22PQBASE_PIC_MODE_TYPE_Ti@plt> @ imm = #-0xb28ec
   f5850: e2000001     	and	r0, r0, #1
   f5854: e54b0015     	strb	r0, [r11, #-0x15]
   f5858: ea000022     	b	0xf58e8 <TvPqManagerService::savePictureMode(unsigned int, TPQSDK_VIDEO_APPLY_T, int, TPQSDK_PICTURE_MODE_PRIORITY_T)+0x240> @ imm = #0x88
   f585c: e3000000     	movw	r0, #0x0
   f5860: e58d0024     	str	r0, [sp, #0x24]
   f5864: e59d0024     	ldr	r0, [sp, #0x24]
   f5868: e3500007     	cmp	r0, #7
   f586c: aa00001c     	bge	0xf58e4 <TvPqManagerService::savePictureMode(unsigned int, TPQSDK_VIDEO_APPLY_T, int, TPQSDK_PICTURE_MODE_PRIORITY_T)+0x23c> @ imm = #0x70
   f5870: e59d0024     	ldr	r0, [sp, #0x24]
   f5874: e3500005     	cmp	r0, #5
   f5878: 1a000000     	bne	0xf5880 <TvPqManagerService::savePictureMode(unsigned int, TPQSDK_VIDEO_APPLY_T, int, TPQSDK_PICTURE_MODE_PRIORITY_T)+0x1d8> @ imm = #0x0
   f587c: ea000014     	b	0xf58d4 <TvPqManagerService::savePictureMode(unsigned int, TPQSDK_VIDEO_APPLY_T, int, TPQSDK_PICTURE_MODE_PRIORITY_T)+0x22c> @ imm = #0x50
   f5880: ebfd3855     	bl	0x439dc <_ZN9ConfigMgr11GetInstanceEv@plt> @ imm = #-0xb1eac
   f5884: e59d1024     	ldr	r1, [sp, #0x24]
   f5888: e59d202c     	ldr	r2, [sp, #0x2c]
   f588c: ebfd3bcd     	bl	0x447c8 <_ZN9ConfigMgr20getPictureModeByTypeE27PQBASE_DYNAMIC_RANGE_TYPE_T27PQBASE_PIC_MODE_NAME_TYPE_T@plt> @ imm = #-0xb10cc
   f5890: e50b0014     	str	r0, [r11, #-0x14]
   f5894: e51b0014     	ldr	r0, [r11, #-0x14]
   f5898: e3500000     	cmp	r0, #0
   f589c: ba00000b     	blt	0xf58d0 <TvPqManagerService::savePictureMode(unsigned int, TPQSDK_VIDEO_APPLY_T, int, TPQSDK_PICTURE_MODE_PRIORITY_T)+0x228> @ imm = #0x2c
   f58a0: e51b0014     	ldr	r0, [r11, #-0x14]
   f58a4: e350003d     	cmp	r0, #61
   f58a8: aa000008     	bge	0xf58d0 <TvPqManagerService::savePictureMode(unsigned int, TPQSDK_VIDEO_APPLY_T, int, TPQSDK_PICTURE_MODE_PRIORITY_T)+0x228> @ imm = #0x20
   f58ac: ebfd3310     	bl	0x424f4 <_ZN9StatusMgr11GetInstanceEv@plt> @ imm = #-0xb33c0
   f58b0: e51b1020     	ldr	r1, [r11, #-0x20]
   f58b4: e59d2024     	ldr	r2, [sp, #0x24]
   f58b8: e51b3014     	ldr	r3, [r11, #-0x14]
   f58bc: e59de028     	ldr	lr, [sp, #0x28]
   f58c0: e58de000     	str	lr, [sp]
   f58c4: ebfd35a7     	bl	0x42f68 <_ZN9StatusMgr22dataSetApplyAllPicModeE19PQBASE_CODEC_TYPE_T27PQBASE_DYNAMIC_RANGE_TYPE_T22PQBASE_PIC_MODE_TYPE_Ti@plt> @ imm = #-0xb2964
   f58c8: e2000001     	and	r0, r0, #1
   f58cc: e54b0015     	strb	r0, [r11, #-0x15]
   f58d0: eaffffff     	b	0xf58d4 <TvPqManagerService::savePictureMode(unsigned int, TPQSDK_VIDEO_APPLY_T, int, TPQSDK_PICTURE_MODE_PRIORITY_T)+0x22c> @ imm = #-0x4
   f58d4: e59d0024     	ldr	r0, [sp, #0x24]
   f58d8: e2800001     	add	r0, r0, #1
   f58dc: e58d0024     	str	r0, [sp, #0x24]
   f58e0: eaffffdf     	b	0xf5864 <TvPqManagerService::savePictureMode(unsigned int, TPQSDK_VIDEO_APPLY_T, int, TPQSDK_PICTURE_MODE_PRIORITY_T)+0x1bc> @ imm = #-0x84
   f58e4: eaffffff     	b	0xf58e8 <TvPqManagerService::savePictureMode(unsigned int, TPQSDK_VIDEO_APPLY_T, int, TPQSDK_PICTURE_MODE_PRIORITY_T)+0x240> @ imm = #-0x4
   f58e8: ea00004e     	b	0xf5a28 <TvPqManagerService::savePictureMode(unsigned int, TPQSDK_VIDEO_APPLY_T, int, TPQSDK_PICTURE_MODE_PRIORITY_T)+0x380> @ imm = #0x138
   f58ec: e51b0024     	ldr	r0, [r11, #-0x24]
   f58f0: e3500005     	cmp	r0, #5
   f58f4: 1a000009     	bne	0xf5920 <TvPqManagerService::savePictureMode(unsigned int, TPQSDK_VIDEO_APPLY_T, int, TPQSDK_PICTURE_MODE_PRIORITY_T)+0x278> @ imm = #0x24
   f58f8: ebfd32fd     	bl	0x424f4 <_ZN9StatusMgr11GetInstanceEv@plt> @ imm = #-0xb340c
   f58fc: e51b1020     	ldr	r1, [r11, #-0x20]
   f5900: e51b3014     	ldr	r3, [r11, #-0x14]
   f5904: e59de028     	ldr	lr, [sp, #0x28]
   f5908: e3002005     	movw	r2, #0x5
   f590c: e58de000     	str	lr, [sp]
   f5910: ebfd3264     	bl	0x422a8 <_ZN9StatusMgr14dataSetPicModeE19PQBASE_CODEC_TYPE_T27PQBASE_DYNAMIC_RANGE_TYPE_T22PQBASE_PIC_MODE_TYPE_Ti@plt> @ imm = #-0xb3670
   f5914: e2000001     	and	r0, r0, #1
   f5918: e54b0015     	strb	r0, [r11, #-0x15]
   f591c: ea000040     	b	0xf5a24 <TvPqManagerService::savePictureMode(unsigned int, TPQSDK_VIDEO_APPLY_T, int, TPQSDK_PICTURE_MODE_PRIORITY_T)+0x37c> @ imm = #0x100
   f5920: e3000000     	movw	r0, #0x0
   f5924: e58d0020     	str	r0, [sp, #0x20]
   f5928: e59d0020     	ldr	r0, [sp, #0x20]
   f592c: e3500007     	cmp	r0, #7
   f5930: aa00003a     	bge	0xf5a20 <TvPqManagerService::savePictureMode(unsigned int, TPQSDK_VIDEO_APPLY_T, int, TPQSDK_PICTURE_MODE_PRIORITY_T)+0x378> @ imm = #0xe8
   f5934: e59d0020     	ldr	r0, [sp, #0x20]
   f5938: e3500005     	cmp	r0, #5
   f593c: 1a000000     	bne	0xf5944 <TvPqManagerService::savePictureMode(unsigned int, TPQSDK_VIDEO_APPLY_T, int, TPQSDK_PICTURE_MODE_PRIORITY_T)+0x29c> @ imm = #0x0
   f5940: ea000032     	b	0xf5a10 <TvPqManagerService::savePictureMode(unsigned int, TPQSDK_VIDEO_APPLY_T, int, TPQSDK_PICTURE_MODE_PRIORITY_T)+0x368> @ imm = #0xc8
   f5944: ebfd3824     	bl	0x439dc <_ZN9ConfigMgr11GetInstanceEv@plt> @ imm = #-0xb1f70
   f5948: e59d1020     	ldr	r1, [sp, #0x20]
   f594c: e59d202c     	ldr	r2, [sp, #0x2c]
   f5950: ebfd3b9c     	bl	0x447c8 <_ZN9ConfigMgr20getPictureModeByTypeE27PQBASE_DYNAMIC_RANGE_TYPE_T27PQBASE_PIC_MODE_NAME_TYPE_T@plt> @ imm = #-0xb1190
   f5954: e50b0014     	str	r0, [r11, #-0x14]
   f5958: e59d0020     	ldr	r0, [sp, #0x20]
   f595c: e3500001     	cmp	r0, #1
   f5960: 1a000012     	bne	0xf59b0 <TvPqManagerService::savePictureMode(unsigned int, TPQSDK_VIDEO_APPLY_T, int, TPQSDK_PICTURE_MODE_PRIORITY_T)+0x308> @ imm = #0x48
   f5964: e51b0014     	ldr	r0, [r11, #-0x14]
   f5968: e3500013     	cmp	r0, #19
   f596c: 1a00000f     	bne	0xf59b0 <TvPqManagerService::savePictureMode(unsigned int, TPQSDK_VIDEO_APPLY_T, int, TPQSDK_PICTURE_MODE_PRIORITY_T)+0x308> @ imm = #0x3c
   f5970: e51b0024     	ldr	r0, [r11, #-0x24]
   f5974: e3500001     	cmp	r0, #1
   f5978: 0a00000c     	beq	0xf59b0 <TvPqManagerService::savePictureMode(unsigned int, TPQSDK_VIDEO_APPLY_T, int, TPQSDK_PICTURE_MODE_PRIORITY_T)+0x308> @ imm = #0x30
   f597c: ebfd3816     	bl	0x439dc <_ZN9ConfigMgr11GetInstanceEv@plt> @ imm = #-0xb1fa8
   f5980: ebfd2e2b     	bl	0x41234 <_ZN9ConfigMgr14getImaxSupportEv@plt> @ imm = #-0xb4754
   f5984: e3100001     	tst	r0, #1
   f5988: 0a000008     	beq	0xf59b0 <TvPqManagerService::savePictureMode(unsigned int, TPQSDK_VIDEO_APPLY_T, int, TPQSDK_PICTURE_MODE_PRIORITY_T)+0x308> @ imm = #0x20
   f598c: e59f00b0     	ldr	r0, [pc, #0xb0]         @ 0xf5a44 <TvPqManagerService::savePictureMode(unsigned int, TPQSDK_VIDEO_APPLY_T, int, TPQSDK_PICTURE_MODE_PRIORITY_T)+0x39c>
   f5990: e08f1000     	add	r1, pc, r0
   f5994: e59f00ac     	ldr	r0, [pc, #0xac]         @ 0xf5a48 <TvPqManagerService::savePictureMode(unsigned int, TPQSDK_VIDEO_APPLY_T, int, TPQSDK_PICTURE_MODE_PRIORITY_T)+0x3a0>
   f5998: e08f2000     	add	r2, pc, r0
   f599c: e300001b     	movw	r0, #0x1b
   f59a0: e50b0014     	str	r0, [r11, #-0x14]
   f59a4: e3000004     	movw	r0, #0x4
   f59a8: ebfd3784     	bl	0x437c0 <__android_log_print@plt> @ imm = #-0xb21f0
   f59ac: e58d0008     	str	r0, [sp, #0x8]
   f59b0: e59f0094     	ldr	r0, [pc, #0x94]         @ 0xf5a4c <TvPqManagerService::savePictureMode(unsigned int, TPQSDK_VIDEO_APPLY_T, int, TPQSDK_PICTURE_MODE_PRIORITY_T)+0x3a4>
   f59b4: e08f1000     	add	r1, pc, r0
   f59b8: e59f0090     	ldr	r0, [pc, #0x90]         @ 0xf5a50 <TvPqManagerService::savePictureMode(unsigned int, TPQSDK_VIDEO_APPLY_T, int, TPQSDK_PICTURE_MODE_PRIORITY_T)+0x3a8>
   f59bc: e08f2000     	add	r2, pc, r0
   f59c0: e51b3014     	ldr	r3, [r11, #-0x14]
   f59c4: e3000004     	movw	r0, #0x4
   f59c8: ebfd377c     	bl	0x437c0 <__android_log_print@plt> @ imm = #-0xb2210
   f59cc: e51b1014     	ldr	r1, [r11, #-0x14]
   f59d0: e3510000     	cmp	r1, #0
   f59d4: e58d0004     	str	r0, [sp, #0x4]
   f59d8: ba00000b     	blt	0xf5a0c <TvPqManagerService::savePictureMode(unsigned int, TPQSDK_VIDEO_APPLY_T, int, TPQSDK_PICTURE_MODE_PRIORITY_T)+0x364> @ imm = #0x2c
   f59dc: e51b0014     	ldr	r0, [r11, #-0x14]
   f59e0: e350003d     	cmp	r0, #61
   f59e4: aa000008     	bge	0xf5a0c <TvPqManagerService::savePictureMode(unsigned int, TPQSDK_VIDEO_APPLY_T, int, TPQSDK_PICTURE_MODE_PRIORITY_T)+0x364> @ imm = #0x20
   f59e8: ebfd32c1     	bl	0x424f4 <_ZN9StatusMgr11GetInstanceEv@plt> @ imm = #-0xb34fc
   f59ec: e51b1020     	ldr	r1, [r11, #-0x20]
   f59f0: e59d2020     	ldr	r2, [sp, #0x20]
   f59f4: e51b3014     	ldr	r3, [r11, #-0x14]
   f59f8: e59de028     	ldr	lr, [sp, #0x28]
   f59fc: e58de000     	str	lr, [sp]
   f5a00: ebfd3228     	bl	0x422a8 <_ZN9StatusMgr14dataSetPicModeE19PQBASE_CODEC_TYPE_T27PQBASE_DYNAMIC_RANGE_TYPE_T22PQBASE_PIC_MODE_TYPE_Ti@plt> @ imm = #-0xb3760
   f5a04: e2000001     	and	r0, r0, #1
   f5a08: e54b0015     	strb	r0, [r11, #-0x15]
   f5a0c: eaffffff     	b	0xf5a10 <TvPqManagerService::savePictureMode(unsigned int, TPQSDK_VIDEO_APPLY_T, int, TPQSDK_PICTURE_MODE_PRIORITY_T)+0x368> @ imm = #-0x4
   f5a10: e59d0020     	ldr	r0, [sp, #0x20]
   f5a14: e2800001     	add	r0, r0, #1
   f5a18: e58d0020     	str	r0, [sp, #0x20]
   f5a1c: eaffffc1     	b	0xf5928 <TvPqManagerService::savePictureMode(unsigned int, TPQSDK_VIDEO_APPLY_T, int, TPQSDK_PICTURE_MODE_PRIORITY_T)+0x280> @ imm = #-0xfc
   f5a20: eaffffff     	b	0xf5a24 <TvPqManagerService::savePictureMode(unsigned int, TPQSDK_VIDEO_APPLY_T, int, TPQSDK_PICTURE_MODE_PRIORITY_T)+0x37c> @ imm = #-0x4
   f5a24: eaffffff     	b	0xf5a28 <TvPqManagerService::savePictureMode(unsigned int, TPQSDK_VIDEO_APPLY_T, int, TPQSDK_PICTURE_MODE_PRIORITY_T)+0x380> @ imm = #-0x4
   f5a28: e55b0015     	ldrb	r0, [r11, #-0x15]
   f5a2c: e2000001     	and	r0, r0, #1
   f5a30: e54b0001     	strb	r0, [r11, #-0x1]
   f5a34: e55b0001     	ldrb	r0, [r11, #-0x1]
   f5a38: e2000001     	and	r0, r0, #1
   f5a3c: e1a0d00b     	mov	sp, r11
   f5a40: e8bd8800     	pop	{r11, pc}
   f5a44: 000a68df     	ldrdeq	r6, r7, [r10], -pc
   f5a48: 000a6924     	andeq	r6, r10, r4, lsr #18
   f5a4c: 000a68bb     	strheq	r6, [r10], -r11
   f5a50: 000a6933     	andeq	r6, r10, r3, lsr r9
   f5a54: 000a6b27     	andeq	r6, r10, r7, lsr #22
   f5a58: 000a6b2b     	andeq	r6, r10, r11, lsr #22
