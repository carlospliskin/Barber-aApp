import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_typography.dart';
import '../../../../core/theme/app_spacing.dart';
import '../../../../shared/widgets/app_button.dart';
import '../../../../shared/widgets/app_text_field.dart';
import '../../../../shared/widgets/app_components.dart';
import '../../domain/entities/dashboard_entities.dart';
import '../../domain/entities/loyalty_entities.dart';
import '../notifiers/home_owner_notifier.dart';

void showNewAppointmentSheet(BuildContext context, WidgetRef ref) {
  showModalBottomSheet(
    context: context,
    isScrollControlled: true,
    backgroundColor: Colors.transparent,
    builder: (_) => ProviderScope(
      parent: ProviderScope.containerOf(context),
      child: const _NewAppointmentSheet(),
    ),
  );
}

class _NewAppointmentSheet extends ConsumerStatefulWidget {
  const _NewAppointmentSheet();

  @override
  ConsumerState<_NewAppointmentSheet> createState() =>
      _NewAppointmentSheetState();
}

class _NewAppointmentSheetState extends ConsumerState<_NewAppointmentSheet> {
  final _nameCtrl  = TextEditingController();
  final _phoneCtrl = TextEditingController();
  bool _isLoading  = false;

  OwnerService? _selectedService;
  BarberWithSchedule? _selectedBarber;
  DateTime? _selectedDateTime;

  @override
  void dispose() {
    _nameCtrl.dispose();
    _phoneCtrl.dispose();
    super.dispose();
  }

  bool get _canSubmit =>
      _nameCtrl.text.trim().isNotEmpty &&
      _phoneCtrl.text.trim().length >= 10 &&
      _selectedService != null &&
      _selectedBarber != null &&
      _selectedDateTime != null;

  @override
  Widget build(BuildContext context) {
    // Datos reales desde el notifier
    final services = ref.watch(homeOwnerNotifierProvider.select((s) => s.services));
    final barbers  = ref.watch(homeOwnerNotifierProvider.select((s) => s.barbers));

    final bottomPadding = MediaQuery.of(context).viewInsets.bottom +
        MediaQuery.of(context).padding.bottom;

    return Container(
      decoration: const BoxDecoration(
        color: AppColors.graphite,
        borderRadius: AppRadius.rBottomSheet,
      ),
      padding: EdgeInsets.only(bottom: bottomPadding),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          // Handle
          Container(
            margin: const EdgeInsets.only(top: AppSpacing.md),
            width: 40, height: 4,
            decoration: BoxDecoration(
              color: AppColors.smoke, borderRadius: AppRadius.rPill),
          ),
          Flexible(
            child: SingleChildScrollView(
              padding: const EdgeInsets.all(AppSpacing.base),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  // Header
                  Row(
                    children: [
                      Expanded(
                        child: Text('Nueva cita',
                          style: AppTypography.displayMd.copyWith(fontSize: 22)),
                      ),
                      GestureDetector(
                        onTap: () => Navigator.pop(context),
                        child: Container(
                          width: 32, height: 32,
                          decoration: BoxDecoration(
                            color: AppColors.slate, borderRadius: AppRadius.rSm),
                          child: const Icon(Icons.close_rounded,
                            size: 16, color: AppColors.silver),
                        ),
                      ),
                    ],
                  ).animate().fadeIn(delay: 50.ms),

                  const SizedBox(height: AppSpacing.xl),

                  // ── Cliente ──────────────────────────────────────────────
                  _SectionLabel('CLIENTE'),
                  const SizedBox(height: AppSpacing.sm),
                  AppTextField(
                    label: 'Nombre del cliente',
                    controller: _nameCtrl,
                    textInputAction: TextInputAction.next,
                    prefixIcon: const Icon(Icons.person_outline_rounded,
                      size: 18, color: AppColors.ash),
                    onChanged: (_) => setState(() {}),
                  ).animate().fadeIn(delay: 80.ms),
                  const SizedBox(height: AppSpacing.sm),
                  AppTextField(
                    label: 'Teléfono',
                    controller: _phoneCtrl,
                    keyboardType: TextInputType.phone,
                    textInputAction: TextInputAction.done,
                    inputFormatters: [FilteringTextInputFormatter.digitsOnly],
                    prefixIcon: const Icon(Icons.phone_outlined,
                      size: 18, color: AppColors.ash),
                    onChanged: (_) => setState(() {}),
                  ).animate().fadeIn(delay: 100.ms),

                  const SizedBox(height: AppSpacing.lg),

                  // ── Servicio — datos reales ───────────────────────────────
                  _SectionLabel('SERVICIO'),
                  const SizedBox(height: AppSpacing.sm),
                  if (services.isEmpty)
                    _EmptyDataTile(
                      label: 'Sin servicios configurados',
                      onTap: () => Navigator.pop(context),
                    )
                  else
                    _SelectionTile(
                      label: _selectedService?.name ?? 'Seleccionar servicio',
                      sublabel: _selectedService != null
                          ? '${_selectedService!.durationLabel} · ${_selectedService!.priceLabel}'
                          : null,
                      icon: Icons.content_cut_rounded,
                      isSelected: _selectedService != null,
                      onTap: () => _selectService(services),
                    ),

                  const SizedBox(height: AppSpacing.md),

                  // ── Barbero — datos reales ────────────────────────────────
                  _SectionLabel('BARBERO'),
                  const SizedBox(height: AppSpacing.sm),
                  _SelectionTile(
                    label: _selectedBarber?.name ?? 'Seleccionar barbero',
                    sublabel: _selectedBarber?.scheduleLabel,
                    icon: Icons.person_rounded,
                    isSelected: _selectedBarber != null,
                    onTap: () => _selectBarber(barbers),
                  ),

                  const SizedBox(height: AppSpacing.md),

                  // ── Fecha y hora ──────────────────────────────────────────
                  _SectionLabel('FECHA Y HORA'),
                  const SizedBox(height: AppSpacing.sm),
                  _SelectionTile(
                    label: _selectedDateTime != null
                        ? _formatDateTime(_selectedDateTime!)
                        : 'Seleccionar horario',
                    icon: Icons.schedule_rounded,
                    isSelected: _selectedDateTime != null,
                    onTap: _pickDateTime,
                  ),

                  // Duración estimada
                  if (_selectedService != null && _selectedDateTime != null) ...[
                    const SizedBox(height: AppSpacing.xs),
                    Padding(
                      padding: const EdgeInsets.only(left: AppSpacing.xs),
                      child: Text(
                        'Finaliza ${_formatTime(_selectedDateTime!.add(Duration(minutes: _selectedService!.durationMinutes)))}',
                        style: AppTypography.labelSm.copyWith(color: AppColors.ash),
                      ),
                    ),
                  ],

                  const SizedBox(height: AppSpacing.xl),

                  // ── Resumen antes de crear ────────────────────────────────
                  if (_canSubmit)
                    _AppointmentSummary(
                      clientName: _nameCtrl.text.trim(),
                      service: _selectedService!,
                      barber: _selectedBarber!,
                      dateTime: _selectedDateTime!,
                    ).animate().fadeIn(),

                  const SizedBox(height: AppSpacing.lg),

                  AppButton(
                    label: 'Crear cita',
                    loading: _isLoading,
                    onTap: _canSubmit ? _submit : null,
                  ),

                  const SizedBox(height: AppSpacing.md),
                ],
              ),
            ),
          ),
        ],
      ),
    ).animate().slideY(begin: 0.3, duration: 350.ms, curve: Curves.easeOutCubic);
  }

  // ─── Submit ────────────────────────────────────────────────────────────────

  Future<void> _submit() async {
    if (!_canSubmit) return;
    setState(() => _isLoading = true);
    HapticFeedback.mediumImpact();

    final result = await ref
        .read(homeOwnerNotifierProvider.notifier)
        .createAppointment(
          clientName:  _nameCtrl.text.trim(),
          clientPhone: _phoneCtrl.text.trim(),
          serviceId:   _selectedService!.id,
          barberId:    _selectedBarber!.memberId,
          startTime:   _selectedDateTime!,
        );

    if (!mounted) return;
    setState(() => _isLoading = false);

    result.fold(
      (failure) => ScaffoldMessenger.of(context).showSnackBar(SnackBar(
        content: Text(failure.userMessage,
          style: AppTypography.bodyMd.copyWith(color: AppColors.white)),
        backgroundColor: AppColors.error,
        behavior: SnackBarBehavior.floating,
        shape: RoundedRectangleBorder(borderRadius: AppRadius.rMd),
      )),
      (created) {
        Navigator.pop(context);
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
          content: Text(
            'Cita creada · Código: ${created.confirmationCode}',
            style: AppTypography.bodyMd.copyWith(color: AppColors.white),
          ),
          backgroundColor: AppColors.confirmed,
          behavior: SnackBarBehavior.floating,
          shape: RoundedRectangleBorder(borderRadius: AppRadius.rMd),
        ));
      },
    );
  }

  // ─── Selección de servicio ────────────────────────────────────────────────

  Future<void> _selectService(List<OwnerService> services) async {
    HapticFeedback.selectionClick();
    final selected = await _showPickerSheet<OwnerService>(
      title: 'Seleccionar servicio',
      items: services,
      labelBuilder: (s) => s.name,
      metaBuilder: (s) => '${s.durationLabel} · ${s.priceLabel}',
      leadingBuilder: (s) => Container(
        width: 8, height: 8,
        decoration: BoxDecoration(shape: BoxShape.circle, color: AppColors.gold),
      ),
    );
    if (selected != null) setState(() => _selectedService = selected);
  }

  // ─── Selección de barbero ─────────────────────────────────────────────────

  Future<void> _selectBarber(List<BarberWithSchedule> barbers) async {
    HapticFeedback.selectionClick();
    // Incluir "Cualquier barbero" disponible
    final workingBarbers = barbers.where((b) => b.isWorkingToday).toList();
    if (workingBarbers.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(
        content: Text('No hay barberos disponibles hoy',
          style: AppTypography.bodyMd.copyWith(color: AppColors.white)),
        backgroundColor: AppColors.slate,
        behavior: SnackBarBehavior.floating,
      ));
      return;
    }

    final selected = await _showPickerSheet<BarberWithSchedule>(
      title: 'Seleccionar barbero',
      items: workingBarbers,
      labelBuilder: (b) => b.name,
      metaBuilder: (b) => b.scheduleLabel,
      leadingBuilder: (b) => Container(
        width: 28, height: 28,
        decoration: BoxDecoration(
          color: AppColors.goldSubtle, shape: BoxShape.circle),
        child: Center(child: Text(
          b.name[0].toUpperCase(),
          style: AppTypography.labelMd.copyWith(
            color: AppColors.gold, fontWeight: FontWeight.w700),
        )),
      ),
    );
    if (selected != null) setState(() => _selectedBarber = selected);
  }

  // ─── Fecha y hora ────────────────────────────────────────────────────────

  Future<void> _pickDateTime() async {
    final date = await showDatePicker(
      context: context,
      initialDate: DateTime.now(),
      firstDate: DateTime.now(),
      lastDate: DateTime.now().add(const Duration(days: 90)),
      builder: (ctx, child) => Theme(
        data: ThemeData.dark().copyWith(colorScheme: const ColorScheme.dark(
          primary: AppColors.gold, onPrimary: AppColors.obsidian,
          surface: AppColors.graphite, onSurface: AppColors.white,
        )),
        child: child!,
      ),
    );
    if (date == null || !mounted) return;

    final time = await showTimePicker(
      context: context,
      initialTime: TimeOfDay.now(),
      builder: (ctx, child) => Theme(
        data: ThemeData.dark().copyWith(colorScheme: const ColorScheme.dark(
          primary: AppColors.gold, onPrimary: AppColors.obsidian,
          surface: AppColors.graphite, onSurface: AppColors.white,
        )),
        child: child!,
      ),
    );
    if (time == null || !mounted) return;

    setState(() {
      _selectedDateTime = DateTime(
        date.year, date.month, date.day, time.hour, time.minute,
      );
    });
  }

  // ─── Picker genérico ─────────────────────────────────────────────────────

  Future<T?> _showPickerSheet<T>({
    required String title,
    required List<T> items,
    required String Function(T) labelBuilder,
    required String Function(T) metaBuilder,
    Widget Function(T)? leadingBuilder,
  }) async {
    return showModalBottomSheet<T>(
      context: context,
      backgroundColor: AppColors.graphite,
      shape: const RoundedRectangleBorder(borderRadius: AppRadius.rBottomSheet),
      builder: (ctx) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              margin: const EdgeInsets.only(top: AppSpacing.md),
              width: 40, height: 4,
              decoration: BoxDecoration(
                color: AppColors.smoke, borderRadius: AppRadius.rPill),
            ),
            Padding(
              padding: const EdgeInsets.all(AppSpacing.base),
              child: Text(title, style: AppTypography.headingMd),
            ),
            ...items.map((item) => ListTile(
              leading: leadingBuilder?.call(item),
              title: Text(labelBuilder(item),
                style: AppTypography.bodyMd.copyWith(color: AppColors.white)),
              trailing: Text(metaBuilder(item),
                style: AppTypography.monoSm.copyWith(color: AppColors.gold)),
              contentPadding: const EdgeInsets.symmetric(
                horizontal: AppSpacing.base),
              onTap: () {
                HapticFeedback.selectionClick();
                Navigator.pop(ctx, item);
              },
            )),
            const SizedBox(height: AppSpacing.md),
          ],
        ),
      ),
    );
  }

  // ─── Helpers de formato ──────────────────────────────────────────────────

  String _formatDateTime(DateTime dt) {
    const months = ['ene','feb','mar','abr','may','jun',
                    'jul','ago','sep','oct','nov','dic'];
    return '${dt.day} ${months[dt.month - 1]} · ${_formatTime(dt)}';
  }

  String _formatTime(DateTime dt) {
    final h = dt.hour.toString().padLeft(2, '0');
    final m = dt.minute.toString().padLeft(2, '0');
    return '$h:$m';
  }
}

// ─── _AppointmentSummary — resumen antes de confirmar ────────────────────────

class _AppointmentSummary extends StatelessWidget {
  final String clientName;
  final OwnerService service;
  final BarberWithSchedule barber;
  final DateTime dateTime;

  const _AppointmentSummary({
    required this.clientName,
    required this.service,
    required this.barber,
    required this.dateTime,
  });

  @override
  Widget build(BuildContext context) {
    final endTime = dateTime.add(Duration(minutes: service.durationMinutes));
    final h  = dateTime.hour.toString().padLeft(2,'0');
    final m  = dateTime.minute.toString().padLeft(2,'0');
    final eh = endTime.hour.toString().padLeft(2,'0');
    final em = endTime.minute.toString().padLeft(2,'0');

    return GoldGlowContainer(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('RESUMEN', style: AppTypography.overline.copyWith(color: AppColors.ash)),
          const SizedBox(height: AppSpacing.sm),
          _Row(icon: Icons.person_rounded,       text: clientName),
          _Row(icon: Icons.content_cut_rounded,  text: service.name),
          _Row(icon: Icons.schedule_rounded,     text: '$h:$m – $eh:$em'),
          _Row(icon: Icons.person_outline_rounded, text: barber.name),
          if (service.hasDeposit)
            _Row(
              icon: Icons.payments_outlined,
              text: 'Depósito \$${service.depositAmount} MXN',
              color: AppColors.gold,
            ),
        ],
      ),
    );
  }
}

class _Row extends StatelessWidget {
  final IconData icon;
  final String text;
  final Color? color;
  const _Row({required this.icon, required this.text, this.color});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: AppSpacing.xs),
      child: Row(
        children: [
          Icon(icon, size: 13, color: color ?? AppColors.ash),
          const SizedBox(width: AppSpacing.sm),
          Expanded(child: Text(text, style: AppTypography.bodySm.copyWith(
            color: color ?? AppColors.pearl, fontSize: 12))),
        ],
      ),
    );
  }
}

// ─── Widgets internos ────────────────────────────────────────────────────────

class _SectionLabel extends StatelessWidget {
  final String label;
  const _SectionLabel(this.label);

  @override
  Widget build(BuildContext context) => Text(
    label,
    style: AppTypography.overline.copyWith(color: AppColors.ash),
  );
}

class _SelectionTile extends StatelessWidget {
  final String label;
  final String? sublabel;
  final IconData icon;
  final bool isSelected;
  final VoidCallback onTap;

  const _SelectionTile({
    required this.label,
    this.sublabel,
    required this.icon,
    required this.isSelected,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 180),
        padding: const EdgeInsets.symmetric(
          horizontal: AppSpacing.md, vertical: AppSpacing.md),
        decoration: BoxDecoration(
          color: isSelected ? AppColors.gold.withOpacity(0.08) : AppColors.slate,
          borderRadius: AppRadius.rInput,
          border: Border.all(
            color: isSelected
                ? AppColors.gold.withOpacity(0.4)
                : AppColors.smoke),
        ),
        child: Row(
          children: [
            Icon(icon, size: 18,
              color: isSelected ? AppColors.gold : AppColors.ash),
            const SizedBox(width: AppSpacing.md),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(label, style: AppTypography.bodyMd.copyWith(
                    color: isSelected ? AppColors.white : AppColors.ash)),
                  if (sublabel != null)
                    Text(sublabel!, style: AppTypography.monoSm.copyWith(
                      fontSize: 11)),
                ],
              ),
            ),
            Icon(Icons.chevron_right_rounded, size: 18, color: AppColors.ash),
          ],
        ),
      ),
    );
  }
}

class _EmptyDataTile extends StatelessWidget {
  final String label;
  final VoidCallback? onTap;
  const _EmptyDataTile({required this.label, this.onTap});

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.all(AppSpacing.md),
        decoration: BoxDecoration(
          color: AppColors.slate,
          borderRadius: AppRadius.rInput,
          border: Border.all(color: AppColors.smoke),
        ),
        child: Text(label, style: AppTypography.bodyMd.copyWith(
          color: AppColors.ash)),
      ),
    );
  }
}
