import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_typography.dart';
import '../../../../core/theme/app_spacing.dart';
import '../../../../features/auth/presentation/notifiers/auth_notifier.dart';
import '../../../../shared/widgets/app_components.dart';
import '../../domain/entities/dashboard_entities.dart';
import '../notifiers/home_owner_notifier.dart';
import '../notifiers/home_owner_state.dart';
import '../widgets/appointment_detail_sheet.dart';
import '../widgets/dashboard_widgets.dart';
import '../widgets/home_owner_widgets.dart';
import '../widgets/new_appointment_sheet.dart';
import 'clients_screen.dart';
import 'config_screen.dart';
import 'manage_loyalty_screen.dart';
import 'manage_schedule_screen.dart';
import 'manage_services_screen.dart';

class HomeOwnerScreen extends ConsumerWidget {
  const HomeOwnerScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final user     = ref.watch(currentUserProvider);
    final state    = ref.watch(homeOwnerNotifierProvider);
    final notifier = ref.read(homeOwnerNotifierProvider.notifier);

    return AnnotatedRegion<SystemUiOverlayStyle>(
      value: SystemUiOverlayStyle.light.copyWith(
        statusBarColor: Colors.transparent,
      ),
      child: Scaffold(
        backgroundColor: AppColors.obsidian,
        body: RefreshIndicator(
          color: AppColors.gold,
          backgroundColor: AppColors.graphite,
          onRefresh: notifier.refresh,
          child: CustomScrollView(
            physics: const AlwaysScrollableScrollPhysics(),
            slivers: [

              // ── Header con apertura ──────────────────────────────────────
              SliverToBoxAdapter(
                child: SafeArea(
                  child: Padding(
                    padding: const EdgeInsets.symmetric(
                      horizontal: AppSpacing.base,
                      vertical: AppSpacing.md,
                    ),
                    child: _Header(
                      userName: user?.displayName.split(' ').first ?? '',
                      tenantName: user?.tenant?.name ?? '',
                      onSwitchToClient: () =>
                          ref.read(authNotifierProvider.notifier).switchMode('CLIENT'),
                      onLogout: () => _showLogoutConfirm(context, ref),
                    ),
                  ),
                ),
              ),

              // ── Métricas ─────────────────────────────────────────────────
              SliverToBoxAdapter(
                child: Padding(
                  padding: AppSpacing.screenHorizontal,
                  child: state.appointmentsLoading
                      ? _MetricsSkeleton()
                      : state.metrics != null
                          ? MetricsStrip(metrics: state.metrics!)
                          : const SizedBox.shrink(),
                ),
              ),

              const SliverToBoxAdapter(child: SizedBox(height: AppSpacing.xl)),

              // ── Servicios en grid ─────────────────────────────────────────
              SliverToBoxAdapter(
                child: Padding(
                  padding: AppSpacing.screenHorizontal,
                  child: ServicesGrid(
                    services: state.services,
                    isLoading: state.servicesLoading,
                    onAddService: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const ManageServicesScreen())),
                  ).animate().fadeIn(delay: 100.ms),
                ),
              ),

              const SliverToBoxAdapter(child: SizedBox(height: AppSpacing.xl)),

              // ── Fidelización con QR ───────────────────────────────────────
              SliverToBoxAdapter(
                child: Padding(
                  padding: AppSpacing.screenHorizontal,
                  child: LoyaltySection(
                    program: state.loyaltyProgram,
                    tenantId: user?.tenant?.id ?? '',
                    isLoading: state.loyaltyLoading,
                    onManageRules: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const ManageLoyaltyScreen())),
                  ).animate().fadeIn(delay: 180.ms),
                ),
              ),

              const SliverToBoxAdapter(child: SizedBox(height: AppSpacing.xl)),

              // ── Horario de hoy ────────────────────────────────────────────
              if (state.barbers.isNotEmpty)
                SliverToBoxAdapter(
                  child: Padding(
                    padding: AppSpacing.screenHorizontal,
                    child: BarbersScheduleWidget(barbers: state.barbers)
                        .animate().fadeIn(delay: 260.ms),
                  ),
                ),

              if (state.barbers.isNotEmpty)
                const SliverToBoxAdapter(child: SizedBox(height: AppSpacing.xl)),

              // ── Selector de fecha ─────────────────────────────────────────
              SliverToBoxAdapter(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Padding(
                      padding: AppSpacing.screenHorizontal,
                      child: Row(
                        children: [
                          Text(
                            'CITAS',
                            style: AppTypography.overline.copyWith(
                              color: AppColors.ash,
                            ),
                          ),
                          if (state.pendingCount > 0) ...[
                            const SizedBox(width: AppSpacing.sm),
                            Container(
                              padding: const EdgeInsets.symmetric(
                                horizontal: AppSpacing.xs + 2,
                                vertical: 2,
                              ),
                              decoration: BoxDecoration(
                                color: AppColors.goldSubtle,
                                borderRadius: AppRadius.rPill,
                              ),
                              child: Text(
                                '${state.pendingCount} pendiente${state.pendingCount > 1 ? 's' : ''}',
                                style: AppTypography.overline.copyWith(
                                  color: AppColors.gold, fontSize: 8,
                                ),
                              ),
                            ),
                          ],
                        ],
                      ),
                    ),
                    const SizedBox(height: AppSpacing.sm),
                    Padding(
                      padding: AppSpacing.screenHorizontal,
                      child: DateSelector(
                        selectedDate: state.selectedDate,
                        onDateChanged: notifier.changeDate,
                      ),
                    ),
                  ],
                ),
              ),

              const SliverToBoxAdapter(child: SizedBox(height: AppSpacing.md)),

              // ── Lista de citas ─────────────────────────────────────────────
              state.appointmentsLoading
                  ? _buildLoadingSliver()
                  : state.failure != null && state.appointments.isEmpty
                      ? _buildErrorSliver(state.failure!.userMessage, notifier)
                      : state.todayAppointments.isEmpty
                          ? _buildEmptySliver(context, ref)
                          : _buildAppointmentsSliver(
                              context, ref, state.todayAppointments),

              const SliverToBoxAdapter(child: SizedBox(height: 100)),
            ],
          ),
        ),
        bottomNavigationBar: _BottomNav(
          onNewAppointment: () => showNewAppointmentSheet(context, ref),
        ),
      ),
    );
  }

  // ─── Slivers ────────────────────────────────────────────────────────────────

  Widget _buildLoadingSliver() {
    return SliverPadding(
      padding: AppSpacing.screenHorizontal,
      sliver: SliverList(
        delegate: SliverChildBuilderDelegate(
          (_, __) => _AppointmentSkeleton(),
          childCount: 4,
        ),
      ),
    );
  }

  Widget _buildErrorSliver(String msg, HomeOwnerNotifier notifier) {
    return SliverToBoxAdapter(
      child: Padding(
        padding: AppSpacing.screenHorizontal,
        child: Container(
          padding: const EdgeInsets.all(AppSpacing.base),
          decoration: BoxDecoration(
            color: AppColors.graphite,
            borderRadius: AppRadius.rCard,
            border: Border.all(color: AppColors.smoke),
          ),
          child: Row(
            children: [
              const Icon(Icons.wifi_off_rounded, size: 18, color: AppColors.ash),
              const SizedBox(width: AppSpacing.md),
              Expanded(
                child: Text(msg, style: AppTypography.bodyMd.copyWith(
                  color: AppColors.silver)),
              ),
              GestureDetector(
                onTap: notifier.refresh,
                child: Text('Reintentar',
                  style: AppTypography.labelMd.copyWith(color: AppColors.gold)),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildEmptySliver(BuildContext context, WidgetRef ref) {
    return SliverToBoxAdapter(
      child: Padding(
        padding: const EdgeInsets.symmetric(
          horizontal: AppSpacing.base,
          vertical: AppSpacing.xl,
        ),
        child: EmptyDayWidget(
          onCreateAppointment: () => showNewAppointmentSheet(context, ref),
        ),
      ),
    );
  }

  Widget _buildAppointmentsSliver(
    BuildContext context,
    WidgetRef ref,
    List<Appointment> appointments,
  ) {
    return SliverPadding(
      padding: AppSpacing.screenHorizontal,
      sliver: SliverList(
        delegate: SliverChildBuilderDelegate(
          (_, i) {
            final apt = appointments[i];
            return AppointmentCard(
              appointment: apt,
              onTap: () => showAppointmentDetail(context, ref, apt),
              onMarkNoShow: apt.isConfirmed
                  ? () => ref
                      .read(homeOwnerNotifierProvider.notifier)
                      .markNoShow(apt.id)
                  : null,
            ).animate().fadeIn(delay: Duration(milliseconds: 60 * i));
          },
          childCount: appointments.length,
        ),
      ),
    );
  }

  Future<void> _showLogoutConfirm(BuildContext context, WidgetRef ref) async {
    final confirm = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: AppColors.graphite,
        shape: RoundedRectangleBorder(borderRadius: AppRadius.rModal),
        title: Text('Cerrar sesión', style: AppTypography.headingMd),
        content: Text('¿Seguro que quieres salir?', style: AppTypography.bodyMd),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: Text('Cancelar', style: AppTypography.labelMd.copyWith(
              color: AppColors.silver)),
          ),
          TextButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: Text('Salir', style: AppTypography.labelMd.copyWith(
              color: AppColors.error, fontWeight: FontWeight.w700)),
          ),
        ],
      ),
    );
    if (confirm == true && context.mounted) {
      ref.read(authNotifierProvider.notifier).logout();
    }
  }
}

// ─── _Header ─────────────────────────────────────────────────────────────────

class _Header extends StatelessWidget {
  final String userName;
  final String tenantName;
  final VoidCallback onSwitchToClient;
  final VoidCallback onLogout;

  const _Header({
    required this.userName,
    required this.tenantName,
    required this.onSwitchToClient,
    required this.onLogout,
  });

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Row(
          children: [
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    _greeting(),
                    style: AppTypography.overline.copyWith(color: AppColors.ash),
                  ),
                  const SizedBox(height: 2),
                  Text(
                    userName,
                    style: AppTypography.displayMd.copyWith(fontSize: 22),
                  ),
                  Text(
                    tenantName,
                    style: AppTypography.monoSm.copyWith(color: AppColors.gold),
                  ),
                ],
              ),
            ),
            // Modo cliente
            GestureDetector(
              onTap: () {
                HapticFeedback.lightImpact();
                onSwitchToClient();
              },
              child: Container(
                padding: const EdgeInsets.symmetric(
                  horizontal: AppSpacing.sm + 2, vertical: AppSpacing.sm),
                decoration: BoxDecoration(
                  color: AppColors.graphite,
                  borderRadius: AppRadius.rPill,
                  border: Border.all(color: AppColors.smoke),
                ),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    const Icon(Icons.person_outline_rounded,
                      size: 13, color: AppColors.silver),
                    const SizedBox(width: 4),
                    Text('Cliente', style: AppTypography.labelSm.copyWith(
                      color: AppColors.silver)),
                  ],
                ),
              ),
            ),
            const SizedBox(width: AppSpacing.sm),
            // Avatar
            GestureDetector(
              onTap: onLogout,
              child: Container(
                width: 36, height: 36,
                decoration: BoxDecoration(
                  gradient: AppColors.goldGradient,
                  shape: BoxShape.circle,
                  border: Border.all(
                    color: AppColors.gold.withOpacity(0.4), width: 1.5),
                ),
                child: Center(
                  child: Text(
                    userName.isNotEmpty ? userName[0].toUpperCase() : 'U',
                    style: AppTypography.labelLg.copyWith(
                      color: AppColors.obsidian,
                      fontWeight: FontWeight.w900,
                    ),
                  ),
                ),
              ),
            ),
          ],
        ),
        const SizedBox(height: AppSpacing.md),
        // Estado de apertura — separado del header para darle peso visual
        Row(
          children: [
            const Expanded(child: OpenStatusWidget()),
            const SizedBox(width: AppSpacing.sm),
            // Botón configurar horario
            GestureDetector(
              onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const ManageScheduleScreen())),
              child: Container(
                padding: const EdgeInsets.symmetric(
                  horizontal: AppSpacing.sm + 2, vertical: AppSpacing.sm),
                decoration: BoxDecoration(
                  color: AppColors.graphite,
                  borderRadius: AppRadius.rPill,
                  border: Border.all(color: AppColors.smoke),
                ),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    const Icon(Icons.schedule_rounded,
                      size: 13, color: AppColors.ash),
                    const SizedBox(width: 4),
                    Text('Horario', style: AppTypography.labelSm.copyWith(
                      color: AppColors.ash)),
                  ],
                ),
              ),
            ),
          ],
        ),
      ],
    ).animate().fadeIn(duration: 400.ms);
  }

  String _greeting() {
    final h = DateTime.now().hour;
    if (h < 12) return 'BUENOS DÍAS';
    if (h < 19) return 'BUENAS TARDES';
    return 'BUENAS NOCHES';
  }
}

// ─── _BottomNav ───────────────────────────────────────────────────────────────

class _BottomNav extends StatelessWidget {
  final VoidCallback onNewAppointment;
  const _BottomNav({required this.onNewAppointment});

  @override
  Widget build(BuildContext context) {
    return Container(
      height: 72,
      decoration: const BoxDecoration(
        color: AppColors.carbon,
        border: Border(top: BorderSide(color: AppColors.smoke)),
      ),
      child: SafeArea(
        top: false,
        child: Row(
          children: [
            _NavItem(icon: Icons.home_rounded, label: 'Hoy', isActive: true),
            _NavItem(icon: Icons.group_outlined, label: 'Clientes',
              onTap: () => Navigator.push(context,
                MaterialPageRoute(builder: (_) => const ClientsScreen()))),
            Expanded(
              child: Center(
                child: GestureDetector(
                  onTap: () {
                    HapticFeedback.mediumImpact();
                    onNewAppointment();
                  },
                  child: Container(
                    width: 50, height: 50,
                    decoration: BoxDecoration(
                      gradient: AppColors.goldGradient,
                      shape: BoxShape.circle,
                      boxShadow: AppColors.shadowGoldBtn,
                    ),
                    child: const Icon(
                      Icons.add_rounded,
                      color: AppColors.obsidian, size: 26,
                    ),
                  ),
                ),
              ),
            ),
            _NavItem(icon: Icons.content_cut_rounded, label: 'Servicios',
              onTap: () => Navigator.push(context,
                MaterialPageRoute(builder: (_) => const ManageServicesScreen()))),
            _NavItem(icon: Icons.settings_outlined, label: 'Config',
              onTap: () => Navigator.push(context,
                MaterialPageRoute(builder: (_) => const ConfigScreen()))),
          ],
        ),
      ),
    );
  }
}

class _NavItem extends StatelessWidget {
  final IconData icon;
  final String label;
  final bool isActive;
  final VoidCallback? onTap;

  const _NavItem({
    required this.icon,
    required this.label,
    this.isActive = false,
    this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return Expanded(
      child: GestureDetector(
        onTap: onTap != null ? () {
          HapticFeedback.selectionClick();
          onTap!();
        } : null,
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(icon, size: 22,
              color: isActive ? AppColors.gold : AppColors.ash),
            const SizedBox(height: 3),
            if (isActive)
              Container(
                width: 4, height: 4,
                decoration: const BoxDecoration(
                  shape: BoxShape.circle, color: AppColors.gold),
              )
            else
              Text(label, style: AppTypography.overline.copyWith(
                fontSize: 8, color: AppColors.ash)),
          ],
        ),
      ),
    );
  }
}

// ─── Skeletons ────────────────────────────────────────────────────────────────

class _AppointmentSkeleton extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.only(bottom: AppSpacing.sm),
      height: 70,
      decoration: BoxDecoration(
        color: AppColors.graphite, borderRadius: AppRadius.rCard),
    ).animate(onPlay: (c) => c.repeat())
      .shimmer(duration: 1200.ms, color: AppColors.slate);
  }
}

class _MetricsSkeleton extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return Row(
      children: List.generate(3, (i) => Expanded(
        child: Container(
          margin: EdgeInsets.only(right: i < 2 ? AppSpacing.sm : 0),
          height: 70,
          decoration: BoxDecoration(
            color: AppColors.graphite, borderRadius: AppRadius.rCard),
        ).animate(onPlay: (c) => c.repeat())
          .shimmer(duration: 1200.ms, color: AppColors.slate),
      )),
    );
  }
}
